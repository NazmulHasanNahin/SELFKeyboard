package com.mykb.keyboard

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/** Automatic backup of learned data + settings to a file the user picked once (for example in Google Drive). */
object Backup {
    private val settingKeys = listOf(
        Prefs.MODE, Prefs.HEIGHT, Prefs.TEXT, Prefs.BAR, Prefs.NUMROW, Prefs.THEME, Prefs.HAPTIC, Prefs.HAPMS, Prefs.SOUND,
        Prefs.PREVIEW, Prefs.SUGGEST, Prefs.LEARN, Prefs.AUTOCAP, Prefs.DBLSPACE, Prefs.AUTOSPACE, Prefs.BNDIGITS,
        Prefs.SHOWUNSUP, Prefs.RECENT
    )

    fun uri(ctx: Context): Uri? = Prefs.sp(ctx).getString(Prefs.BACKUP_URI, null)?.let { Uri.parse(it) }

    fun build(ctx: Context): String {
        val root = JSONObject(Store.get(ctx).exportJson())
        val sp = Prefs.sp(ctx)
        val st = JSONObject()
        for (k in settingKeys) {
            val v = sp.all[k] ?: continue
            st.put(k, v)
        }
        root.put("settings", st)
        return root.toString()
    }

    fun restore(ctx: Context, text: String): Int {
        val n = Store.get(ctx).importJson(text)
        val st = JSONObject(text).optJSONObject("settings")
        if (st != null) {
            val e = Prefs.sp(ctx).edit()
            for (k in settingKeys) {
                if (!st.has(k)) continue
                when (val v = st.get(k)) {
                    is Boolean -> e.putBoolean(k, v)
                    is Int -> e.putInt(k, v)
                    is Long -> e.putLong(k, v)
                    is String -> e.putString(k, v)
                }
            }
            e.apply()
            Prefs.touch(ctx)
        }
        return n
    }

    /** Writes the backup file now. Returns an empty string on success, otherwise an error message. */
    fun runNow(ctx: Context): String {
        val u = uri(ctx) ?: return "ব্যাকআপ ফাইল বাছা হয়নি"
        return try {
            val json = build(ctx)
            ctx.contentResolver.openOutputStream(u, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                ?: return "ফাইল খোলা যায়নি"
            Prefs.sp(ctx).edit().putLong(Prefs.BACKUP_LAST, System.currentTimeMillis()).putString(Prefs.BACKUP_MSG, "ঠিক আছে").apply()
            ""
        } catch (e: Exception) {
            val m = e.message ?: "ত্রুটি"
            Prefs.sp(ctx).edit().putString(Prefs.BACKUP_MSG, "ব্যর্থ: $m").apply()
            m
        }
    }

    /** Called when the keyboard closes: backs up at most every 3 hours, in the background. */
    fun maybe(ctx: Context) {
        if (uri(ctx) == null) return
        val last = Prefs.sp(ctx).getLong(Prefs.BACKUP_LAST, 0)
        if (System.currentTimeMillis() - last < 3L * 3600 * 1000) return
        val app = ctx.applicationContext
        Thread { runNow(app) }.start()
    }
}
