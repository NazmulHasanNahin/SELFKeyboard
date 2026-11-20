package com.mykb.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

class Th(
    val bg: Int, val key: Int, val ctrl: Int, val pressed: Int,
    val accent: Int, val text: Int, val dim: Int
)

object Prefs {
    const val MODE = "mode"
    const val HEIGHT = "height"        // percent, 70..140
    const val TEXT = "textsize"        // percent, 80..130
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
    const val RECENT = "recent_emoji"
    const val STAMP = "stamp"

    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("kb", Context.MODE_PRIVATE)

    fun touch(c: Context) {
        sp(c).edit().putLong(STAMP, System.currentTimeMillis()).apply()
    }

    val themeNames = listOf("ডার্ক", "লাইট", "অ্যামোলেড (কালো)", "নীল")

    val themes: List<Th> = listOf(
        Th(Color.parseColor("#1B1B1F"), Color.parseColor("#3B3B43"), Color.parseColor("#2A2A30"),
            Color.parseColor("#66667A"), Color.parseColor("#4C7DF0"), Color.parseColor("#FFFFFF"), Color.parseColor("#A0A0AA")),
        Th(Color.parseColor("#E6E8EC"), Color.parseColor("#FFFFFF"), Color.parseColor("#CBD0D8"),
            Color.parseColor("#AEB5C1"), Color.parseColor("#2F6BF0"), Color.parseColor("#14161A"), Color.parseColor("#5F6672")),
        Th(Color.parseColor("#000000"), Color.parseColor("#1E1E1E"), Color.parseColor("#101010"),
            Color.parseColor("#4A4A4A"), Color.parseColor("#3D6FD9"), Color.parseColor("#FFFFFF"), Color.parseColor("#8E8E8E")),
        Th(Color.parseColor("#0F2A4A"), Color.parseColor("#22507F"), Color.parseColor("#173B63"),
            Color.parseColor("#4B84BF"), Color.parseColor("#F5A623"), Color.parseColor("#FFFFFF"), Color.parseColor("#9DB6D3"))
    )

    fun theme(c: Context): Th = themes[sp(c).getInt(THEME, 0).coerceIn(0, themes.size - 1)]
}
