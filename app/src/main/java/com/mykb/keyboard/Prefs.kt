package com.mykb.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

class Th(
    val bg: Int, val key: Int, val special: Int, val ctrl: Int, val pressed: Int,
    val accent: Int, val text: Int, val dim: Int
)

object Prefs {
    const val MODE = "mode"
    const val HEIGHT = "height"        // percent, 60..220
    const val TEXT = "textsize"        // percent, 70..200
    const val BAR = "barsize"          // percent, 80..200 (toolbar + suggestion bar)
    const val NUMROW = "numrow"
    const val THEME = "theme"
    const val HAPTIC = "haptic"
    const val HAPMS = "hapms"
    const val SOUND = "sound"
    const val PREVIEW = "preview"
    const val SUGGEST = "suggest"
    const val LEARN = "learn"
    const val AUTOCAP = "autocap"
    const val DBLSPACE = "dblspace"
    const val AUTOSPACE = "autospace"
    const val BNDIGITS = "bndigits"
    const val SHOWUNSUP = "showunsup"
    const val RECENT = "recent_emoji"
    const val STAMP = "stamp"
    const val BACKUP_URI = "backup_uri"
    const val BACKUP_LAST = "backup_last"
    const val BACKUP_MSG = "backup_msg"

    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("kb", Context.MODE_PRIVATE)

    fun touch(c: Context) {
        sp(c).edit().putLong(STAMP, System.currentTimeMillis()).apply()
    }

    val themeNames = listOf("Gboard ডার্ক", "ডার্ক (ধূসর)", "লাইট", "অ্যামোলেড (কালো)", "নীল")

    private fun c(s: String) = Color.parseColor(s)

    val themes: List<Th> = listOf(
        Th(c("#11191D"), c("#222B30"), c("#1F4960"), c("#1A242A"), c("#3C4C55"), c("#1F4960"), c("#D3DDE5"), c("#8FA0AB")),
        Th(c("#1B1B1F"), c("#3B3B43"), c("#2A2A30"), c("#2A2A30"), c("#66667A"), c("#4C7DF0"), c("#FFFFFF"), c("#A0A0AA")),
        Th(c("#E6E8EC"), c("#FFFFFF"), c("#CBD0D8"), c("#CBD0D8"), c("#AEB5C1"), c("#2F6BF0"), c("#14161A"), c("#5F6672")),
        Th(c("#000000"), c("#1E1E1E"), c("#141414"), c("#101010"), c("#4A4A4A"), c("#3D6FD9"), c("#FFFFFF"), c("#8E8E8E")),
        Th(c("#0F2A4A"), c("#22507F"), c("#173B63"), c("#173B63"), c("#4B84BF"), c("#F5A623"), c("#FFFFFF"), c("#9DB6D3"))
    )

    fun theme(ctx: Context): Th = themes[sp(ctx).getInt(THEME, 0).coerceIn(0, themes.size - 1)]
}
