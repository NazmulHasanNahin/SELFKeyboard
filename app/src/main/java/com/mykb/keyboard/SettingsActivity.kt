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
    private lateinit var backupStatus: TextView
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
        val presets = listOf(
            Triple("ছোট", 85, 90), Triple("মাঝারি", 100, 100), Triple("বড়", 125, 125),
            Triple("অনেক বড়", 150, 150), Triple("বিশাল", 180, 175), Triple("সর্বোচ্চ", 210, 200)
        )
        button("সাইজ প্রিসেট বাছুন (ছোট থেকে সর্বোচ্চ)") {
            AlertDialog.Builder(this)
                .setTitle("কিবোর্ডের সাইজ")
                .setItems(presets.map { it.first + "  (উচ্চতা ${it.second}%, অক্ষর ${it.third}%)" }.toTypedArray()) { _, which ->
                    val p = presets[which]
                    sp.edit().putInt(Prefs.HEIGHT, p.second).putInt(Prefs.TEXT, p.third)
                        .putInt(Prefs.BAR, (p.second + p.third) / 2).apply()
                    Prefs.touch(this)
                    recreate()
                }
                .show()
        }
        seekRow("কিবোর্ডের উচ্চতা (কী-র সারি)", Prefs.HEIGHT, 60, 220, 100, "%")
        seekRow("অক্ষর/আইকনের সাইজ", Prefs.TEXT, 70, 200, 100, "%")
        seekRow("উপরের টুলবার ও সাজেশন বারের সাইজ", Prefs.BAR, 80, 200, 100, "%")
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

        switchRow("ফোনে যে ইমোজি দেখা যায় না সেগুলোও তালিকায় রাখুন", Prefs.SHOWUNSUP, false)

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
        backupStatus = TextView(this)
        backupStatus.textSize = 14f
        backupStatus.setTextColor(0xFF444444.toInt())
        backupStatus.setPadding(0, dp(4), 0, dp(8))
        col.addView(backupStatus)
        note("একবার একটা ব্যাকআপ ফাইল বাছুন (যেমন Google Drive → My Drive-এ)। এরপর কিবোর্ড নিজে থেকেই প্রতি কয়েক ঘণ্টায় সেটা আপডেট করবে।")
        button("অটো ব্যাকআপ ফাইল বাছুন (Google Drive-এ রাখুন)") {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/json"
            i.putExtra(Intent.EXTRA_TITLE, "mykeyboard-backup.json")
            i.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            startActivityForResult(i, 1)
        }
        button("এখনই ব্যাকআপ নিন") {
            Thread {
                val m = Backup.runNow(applicationContext)
                runOnUiThread {
                    Toast.makeText(this, if (m.isEmpty()) "ব্যাকআপ হয়েছে" else "ব্যর্থ: $m", Toast.LENGTH_LONG).show()
                    refreshStatus()
                }
            }.start()
        }
        button("ব্যাকআপ ফাইল থেকে রিস্টোর (নতুন ফোনে)") {
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
        val u = Backup.uri(this)
        val last = sp.getLong(Prefs.BACKUP_LAST, 0)
        val whenTxt = if (last == 0L) "এখনো হয়নি" else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(last))
        backupStatus.text = if (u == null) "অটো ব্যাকআপ: চালু নেই" else
            "অটো ব্যাকআপ: চালু\nশেষ ব্যাকআপ: $whenTxt\nঅবস্থা: " + (sp.getString(Prefs.BACKUP_MSG, "") ?: "")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == 1) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: Exception) {
                }
                sp.edit().putString(Prefs.BACKUP_URI, uri.toString()).putLong(Prefs.BACKUP_LAST, 0).apply()
                Thread {
                    val m = Backup.runNow(applicationContext)
                    runOnUiThread {
                        Toast.makeText(this, if (m.isEmpty()) "অটো ব্যাকআপ চালু হয়েছে" else "ব্যর্থ: $m", Toast.LENGTH_LONG).show()
                        refreshStatus()
                    }
                }.start()
            } else if (requestCode == 2) {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                val n = Backup.restore(this, text)
                Toast.makeText(this, "রিস্টোর হয়েছে ($n টি এন্ট্রি)", Toast.LENGTH_LONG).show()
                recreate()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "ব্যর্থ: " + e.message, Toast.LENGTH_LONG).show()
        }
        refreshStatus()
    }
}
