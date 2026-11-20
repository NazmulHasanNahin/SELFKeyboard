package com.mykb.keyboard

import android.content.Context

/** Phonetic "skeletons": consonant classes only, so that sh/s, kh/k, vowels etc. do not matter. */
object Phon {
    private val bmap: Map<Char, Char> = mapOf(
        'ক' to 'k', 'খ' to 'k', 'গ' to 'g', 'ঘ' to 'g', 'ঙ' to 'n',
        'চ' to 'c', 'ছ' to 'c', 'জ' to 'j', 'ঝ' to 'j', 'ঞ' to 'n',
        'ট' to 't', 'ঠ' to 't', 'ত' to 't', 'থ' to 't', 'ৎ' to 't',
        'ড' to 'd', 'ঢ' to 'd', 'দ' to 'd', 'ধ' to 'd',
        'ণ' to 'n', 'ন' to 'n', 'প' to 'p', 'ফ' to 'p', 'ব' to 'b', 'ভ' to 'b', 'ম' to 'm',
        'য' to 'j', 'র' to 'r', 'ল' to 'l', 'শ' to 's', 'ষ' to 's', 'স' to 's', 'হ' to 'h',
        'ং' to 'n', '\u09DC' to 'r', '\u09DD' to 'r', '\u09DF' to 'y'
    )

    fun bangla(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == 'ক' && i + 2 < s.length && s[i + 1] == '্' && s[i + 2] == 'ষ') {
                sb.append("kk")
                i += 3
                continue
            }
            val m = bmap[c]
            if (m != null) {
                val nukta = i + 1 < s.length && s[i + 1] == '\u09BC'
                if (nukta && (c == 'ড' || c == 'ঢ')) sb.append('r')
                else if (nukta && c == 'য') sb.append('y')
                else sb.append(m)
                if (nukta) i++
            }
            i++
        }
        return sb.toString()
    }

    fun latin(raw: String): String {
        val s = raw.lowercase()
        val sb = StringBuilder()
        for (i in s.indices) {
            val c = s[i]
            if (!c.isLetter() || c in "aeiou") continue
            val m = when (c) {
                'k', 'q', 'x' -> 'k'
                'g' -> 'g'
                'c' -> 'c'
                'j', 'z' -> 'j'
                't' -> 't'
                'd' -> 'd'
                'n' -> 'n'
                'p', 'f' -> 'p'
                'b', 'v' -> 'b'
                'm' -> 'm'
                'r' -> 'r'
                'l' -> 'l'
                's' -> 's'
                'h' -> 'h'
                'y' -> 'y'
                'w' -> 'w'
                else -> ' '
            }
            if (m == ' ') continue
            if (c == 'h' && i > 0 && s[i - 1] in "kgcjtdpbs") continue
            if (c == 'g' && i > 0 && s[i - 1] == 'n') continue
            sb.append(m)
        }
        return sb.toString()
    }
}

class Hit(val word: String, val rank: Int)

class Dict(private val ctx: Context) {
    @Volatile
    var ready = false
        private set

    private var en: Array<String> = emptyArray()
    private var enSorted = IntArray(0)
    private var bn: Array<String> = emptyArray()
    private var bnSorted = IntArray(0)
    private var bnSkel: Array<String> = emptyArray()
    private var bnSkelSorted = IntArray(0)

    init {
        Thread {
            try {
                load()
            } catch (e: Exception) {
            }
        }.start()
    }

    private fun readLines(name: String): Array<String> {
        val l = ArrayList<String>()
        ctx.assets.open(name).bufferedReader(Charsets.UTF_8).use { r ->
            var s = r.readLine()
            while (s != null) {
                if (s.isNotEmpty()) l.add(s)
                s = r.readLine()
            }
        }
        return l.toTypedArray()
    }

    private fun sortedIdx(a: Array<String>): IntArray =
        (0 until a.size).sortedWith(Comparator { x, y -> a[x].compareTo(a[y]) }).toIntArray()

    private fun load() {
        val e = readLines("en_words.txt")
        val b = readLines("bn_words.txt")
        val sk = Array(b.size) { Phon.bangla(b[it]) }
        val es = sortedIdx(e)
        val bs = sortedIdx(b)
        val ss = sortedIdx(sk)
        en = e; enSorted = es
        bn = b; bnSorted = bs
        bnSkel = sk; bnSkelSorted = ss
        ready = true
    }

    private fun lowerBound(sorted: IntArray, keys: Array<String>, prefix: String): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[sorted[mid]] < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun collect(sorted: IntArray, keys: Array<String>, prefix: String, limit: Int): List<Hit> {
        if (prefix.isEmpty()) return emptyList()
        val out = ArrayList<Hit>()
        var i = lowerBound(sorted, keys, prefix)
        var scanned = 0
        while (i < sorted.size && scanned < 6000) {
            val idx = sorted[i]
            if (!keys[idx].startsWith(prefix)) break
            out.add(Hit(idx.toString(), idx))
            scanned++
            i++
        }
        out.sortBy { it.rank }
        return if (out.size > limit) out.subList(0, limit) else out
    }

    /** English prefix matches (lower-case input), most frequent first. */
    fun enPrefix(prefix: String, limit: Int): List<Hit> {
        if (!ready) return emptyList()
        return collect(enSorted, en, prefix, limit).map { Hit(en[it.rank], it.rank) }
    }

    /** English words at edit distance 1 from the input (typo help), most frequent first. */
    fun enFuzzy(word: String, limit: Int): List<Hit> {
        if (!ready || word.length < 3) return emptyList()
        val out = ArrayList<Hit>()
        for (i in en.indices) {
            val w = en[i]
            if (w.length < word.length - 1 || w.length > word.length + 1) continue
            if (w != word && withinOne(word, w)) {
                out.add(Hit(w, i))
                if (out.size >= limit) break
            }
        }
        return out
    }

    private fun withinOne(a: String, b: String): Boolean {
        if (a.length == b.length) {
            var d = 0
            for (i in a.indices) if (a[i] != b[i]) {
                d++
                if (d > 1) {
                    // allow one adjacent transposition
                    return i > 0 && i + 1 < a.length && a[i - 1] == b[i] && a[i] == b[i - 1] &&
                        a.substring(i + 1) == b.substring(i + 1) && a.substring(0, i - 1) == b.substring(0, i - 1)
                }
            }
            return d == 1
        }
        val s = if (a.length < b.length) a else b
        val l = if (a.length < b.length) b else a
        var i = 0
        while (i < s.length && s[i] == l[i]) i++
        return s.substring(i) == l.substring(i + 1)
    }

    fun bnPrefix(prefix: String, limit: Int): List<Hit> {
        if (!ready) return emptyList()
        return collect(bnSorted, bn, prefix, limit).map { Hit(bn[it.rank], it.rank) }
    }

    /** Bangla words whose phonetic skeleton starts with the given skeleton. */
    fun bnSkelPrefix(skel: String, limit: Int): List<Hit> {
        if (!ready) return emptyList()
        return collect(bnSkelSorted, bnSkel, skel, limit).map { Hit(bn[it.rank], it.rank) }
    }
}
