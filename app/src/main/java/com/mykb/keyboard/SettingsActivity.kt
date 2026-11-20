package com.mykb.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var sp: SharedPreferences
    private lateinit var col: LinearLayout

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun section(title: String) {
        val t = TextView(this)
        t.text = title
        t.textSize = 16f
        t.setTypeface(null, android.graphics.Typeface.BOLD)
        t.setTextColor(0xFF2F6BF0.toInt())
        t.setPadding(0, dp(22), 0, dp(6))
        col.addView(t)
    }

    private fun note(text: String) {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.setTextColor(0xFF666666.toInt())
        t.setPadding(0, 0, 0, dp(4))
        col.addView(t)
    }

    private fun switchRow(label: String, key: String, def: Boolean) {
        val s = Switch(this)
        s.text = label
        s.textSize = 16f
        s.isChecked = sp.getBoolean(key, def)
        s.setPadding(0, dp(8), 0, dp(8))
        s.setOnCheckedChangeListener { _: CompoundButton, on: Boolean ->
            sp.edit().putBoolean(key, on).apply()
            Prefs.touch(this)
        }
        col.addView(s)
    }

    private fun seekRow(label: String, key: String, min: Int, max: Int, def: Int, suffix: String) {
        val tv = TextView(this)
        tv.textSize = 16f
        tv.setPadding(0, dp(8), 0, 0)
        val cur = sp.getInt(key, def).coerceIn(min, max)
        tv.text = "$label: $cur$suffix"
        col.addView(tv)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = cur - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tv.text = "$label: ${p + min}$suffix"
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                sp.edit().putInt(key, (s?.progress ?: 0) + min).apply()
                Prefs.touch(this@SettingsActivity)
            }
        })
        col.addView(sb)
    }

    private fun button(label: String, action: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { action() }
        col.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sp = Prefs.sp(this)
        val sv = ScrollView(this)
        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(36), dp(20), dp(30))
        sv.addView(col)

        val title = TextView(this)
        title.text = "MyKeyboard"
        title.textSize = 24f
        col.addView(title)

        section("সেটআপ")
        button("১. কিবোর্ড চালু করুন (MyKeyboard অন করুন)") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        button("২. এই কিবোর্ড বেছে নিন") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        section("চেহারা ও সাইজ")
        seekRow("কিবোর্ডের উচ্চতা", Prefs.HEIGHT, 70, 140, 100, "%")
        seekRow("অক্ষরের সাইজ", Prefs.TEXT, 80, 130, 100, "%")
        switchRow("উপরে নাম্বার রো (১-০) দেখান", Prefs.NUMROW, true)
        val themeBtn = button("থিম: " + Prefs.themeNames[sp.getInt(Prefs.THEME, 0).coerceIn(0, Prefs.themeNames.size - 1)]) {}
        themeBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("থিম বেছে নিন")
                .setItems(Prefs.themeNames.toTypedArray()) { _, which ->
                    sp.edit().putInt(Prefs.THEME, which).apply()
                    Prefs.touch(this)
                    themeBtn.text = "থিম: " + Prefs.themeNames[which]
                }
                .show()
        }

        section("কী চাপার অনুভূতি")
        switchRow("ভাইব্রেশন", Prefs.HAPTIC, true)
        seekRow("ভাইব্রেশনের জোর", Prefs.HAPMS, 5, 40, 15, " ms")
        switchRow("কী চাপার শব্দ", Prefs.SOUND, false)
        switchRow("চাপলে অক্ষরের পপআপ দেখান", Prefs.PREVIEW, true)

        section("টাইপিং")
        switchRow("সাজেশন বার দেখান", Prefs.SUGGEST, true)
        switchRow("আমার টাইপ করা শব্দ শিখুক (লার্নিং)", Prefs.LEARN, true)
        switchRow("বাক্যের শুরুতে অটো বড় হাতের অক্ষর (English)", Prefs.AUTOCAP, true)
        switchRow("স্পেস দুবার চাপলে দাঁড়ি/ফুলস্টপ", Prefs.DBLSPACE, true)
        switchRow("সাজেশন বাছলে পরে অটো স্পেস", Prefs.AUTOSPACE, true)
        switchRow("অভ্র মোডে নাম্বার বাংলায় (১ ২ ৩)", Prefs.BNDIGITS, false)
        note("টিপস: কী চেপে ধরলে ছোট অক্ষর/চিহ্ন বসে। স্পেস চেপে ধরলে কিবোর্ড বদলানোর মেনু আসে। ভাষার বাটন চেপে ধরলে সরাসরি ভাষা বাছা যায়।")

        status = TextView(this)
        status.textSize = 15f
        status.setPadding(0, dp(14), 0, dp(6))
        section("ডাটা ও ব্যাকআপ")
        col.addView(status)
        button("ব্যাকআপ এক্সপোর্ট (JSON ফাইল সেভ)") {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/json"
            i.putExtra(Intent.EXTRA_TITLE, "mykeyboard-backup.json")
            startActivityForResult(i, 1)
        }
        button("ব্যাকআপ ইমপোর্ট (JSON ফাইল থেকে রিস্টোর)") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, 2)
        }
        button("শেখা শব্দ/ডাটা মুছে ফেলুন") {
            AlertDialog.Builder(this)
                .setTitle("নিশ্চিত?")
                .setMessage("শেখা সব শব্দ ও টাইপিং প্যাটার্ন মুছে যাবে (ক্লিপবোর্ড থাকবে)। আগে ব্যাকআপ নিয়ে নিন।")
                .setPositiveButton("মুছুন") { _, _ ->
                    Store.get(this).resetLearning()
                    refreshStatus()
                }
                .setNegativeButton("বাতিল", null)
                .show()
        }

        section("টেস্ট করুন")
        val test = EditText(this)
        test.hint = "এখানে টাইপ করে দেখুন"
        test.gravity = Gravity.TOP
        test.minLines = 3
        col.addView(test)

        setContentView(sv)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        status.text = "শেখা শব্দ সংখ্যা: " + Store.get(this).wordCount()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == 1) {
                val json = Store.get(this).exportJson()
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "ব্যাকআপ সেভ হয়েছে", Toast.LENGTH_LONG).show()
            } else if (requestCode == 2) {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                val n = Store.get(this).importJson(text)
                Toast.makeText(this, "রিস্টোর হয়েছে ($n টি এন্ট্রি)", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "ব্যর্থ: " + e.message, Toast.LENGTH_LONG).show()
        }
        refreshStatus()
    }
}
