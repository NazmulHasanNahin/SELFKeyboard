package com.mykb.keyboard

/** Simplified Avro-style phonetic converter: Latin letters -> Bangla. */
object Avro {
    private const val NUKTA_RA = "\u09DC" // ড়
    private const val NUKTA_RHA = "\u09DD" // ঢ়
    private const val YA = "\u09DF" // য়

    private val cons: Map<String, String> = mapOf(
        "kkh" to "ক্ষ", "kh" to "খ", "k" to "ক", "q" to "ক", "x" to "ক্স",
        "gh" to "ঘ", "g" to "গ", "Ng" to "ঙ", "NG" to "ঞ",
        "chh" to "ছ", "ch" to "ছ", "c" to "চ",
        "jh" to "ঝ", "j" to "জ", "z" to "জ", "Z" to "য",
        "Th" to "ঠ", "T" to "ট", "Dh" to "ঢ", "D" to "ড", "N" to "ণ",
        "th" to "থ", "t" to "ত", "dh" to "ধ", "d" to "দ", "n" to "ন",
        "ph" to "ফ", "p" to "প", "f" to "ফ", "bh" to "ভ", "b" to "ব", "v" to "ভ", "m" to "ম",
        "y" to YA, "r" to "র", "R" to NUKTA_RA, "Rh" to NUKTA_RHA, "l" to "ল", "w" to "ওয়",
        "sh" to "শ", "Sh" to "ষ", "S" to "শ", "s" to "স", "h" to "হ"
    )

    // latin -> (independent vowel, kar sign)
    private val vowels: Map<String, Pair<String, String>> = mapOf(
        "rri" to Pair("ঋ", "ৃ"),
        "aa" to Pair("আ", "া"),
        "ii" to Pair("ঈ", "ী"),
        "uu" to Pair("ঊ", "ূ"),
        "oi" to Pair("ঐ", "ৈ"),
        "ou" to Pair("ঔ", "ৌ"),
        "ee" to Pair("ঈ", "ী"),
        "oo" to Pair("ঊ", "ূ"),
        "o" to Pair("অ", ""),
        "a" to Pair("আ", "া"),
        "i" to Pair("ই", "ি"),
        "I" to Pair("ঈ", "ী"),
        "u" to Pair("উ", "ু"),
        "U" to Pair("ঊ", "ূ"),
        "e" to Pair("এ", "ে"),
        "O" to Pair("ও", "ো")
    )

    fun convert(raw: String): String {
        val sb = StringBuilder()
        var prevC = false
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch in '0'..'9') {
                sb.append('০' + (ch - '0'))
                prevC = false
                i++
                continue
            }
            if (ch == '^') {
                sb.append("ঁ")
                prevC = false
                i++
                continue
            }
            if (ch == ':') {
                sb.append("ঃ")
                prevC = false
                i++
                continue
            }
            if (raw.startsWith("t``", i)) {
                sb.append("ৎ")
                prevC = false
                i += 3
                continue
            }
            if (ch == '`') {
                i++
                continue
            }
            if (raw.startsWith("ng", i)) {
                val nxt = if (i + 2 < raw.length) raw[i + 2] else ' '
                if (nxt == 'g' || nxt == 'k') {
                    if (prevC) sb.append('্')
                    sb.append("ঙ")
                    prevC = true
                } else {
                    sb.append("ং")
                    prevC = false
                }
                i += 2
                continue
            }
            var matched = false
            for (len in 3 downTo 1) {
                if (i + len > raw.length) continue
                val s = raw.substring(i, i + len)
                val c = cons[s]
                if (c != null) {
                    if (prevC) sb.append('্')
                    sb.append(if (s == "y" && prevC) "য" else c)
                    prevC = true
                    i += len
                    matched = true
                    break
                }
                val v = vowels[s]
                if (v != null) {
                    if (s == "o" && prevC && i + len == raw.length) sb.append("ো") // word-final o: valo -> ভালো
                    else sb.append(if (prevC) v.second else v.first)
                    prevC = false
                    i += len
                    matched = true
                    break
                }
            }
            if (!matched) {
                sb.append(ch)
                prevC = false
                i++
            }
        }
        return sb.toString()
    }
}
