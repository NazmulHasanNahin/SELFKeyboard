package com.mykb.keyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

const val MODE_EN = 0
const val MODE_BN = 1
const val MODE_AVRO = 2

data class Clip(val id: Long, val text: String, val pinned: Boolean)

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "kb.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE words(w TEXT PRIMARY KEY, c INTEGER NOT NULL, t INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE bigrams(p TEXT NOT NULL, n TEXT NOT NULL, c INTEGER NOT NULL, PRIMARY KEY(p,n))")
        db.execSQL("CREATE TABLE shortcuts(k TEXT NOT NULL, w TEXT NOT NULL, c INTEGER NOT NULL, PRIMARY KEY(k,w))")
        db.execSQL("CREATE TABLE clips(id INTEGER PRIMARY KEY AUTOINCREMENT, txt TEXT NOT NULL UNIQUE, t INTEGER NOT NULL, pin INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE trigrams(a TEXT NOT NULL, b TEXT NOT NULL, n TEXT NOT NULL, c INTEGER NOT NULL, PRIMARY KEY(a,b,n))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS trigrams(a TEXT NOT NULL, b TEXT NOT NULL, n TEXT NOT NULL, c INTEGER NOT NULL, PRIMARY KEY(a,b,n))")
            db.execSQL("DELETE FROM words WHERE t=0") // old built-in seed words (now in the dictionary)
        }
    }
}

object Store {
    @Volatile
    private var inst: Brain? = null

    fun get(ctx: Context): Brain {
        val i = inst
        if (i != null) return i
        return synchronized(this) {
            val j = inst
            if (j != null) j else {
                val b = Brain(ctx.applicationContext, Db(ctx.applicationContext))
                inst = b
                b
            }
        }
    }
}

class Brain(ctx: Context, private val dbh: Db) {
    val dict = Dict(ctx)
    private val trigrams = HashMap<String, HashMap<String, Int>>()
    private val lock = Any()
    private val words = HashMap<String, Int>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private val shorts = HashMap<String, HashMap<String, Int>>()
    private val exec = Executors.newSingleThreadExecutor()

    init {
        exec.execute {
            try {
                load()
            } catch (e: Exception) {
            }
        }
    }

    private fun load() {
        val db = dbh.readableDatabase
        synchronized(lock) {
            db.rawQuery("SELECT w,c FROM words", null).use { c ->
                while (c.moveToNext()) words[c.getString(0)] = c.getInt(1)
            }
            db.rawQuery("SELECT p,n,c FROM bigrams", null).use { c ->
                while (c.moveToNext()) bigrams.getOrPut(c.getString(0)) { HashMap() }[c.getString(1)] = c.getInt(2)
            }
            db.rawQuery("SELECT k,w,c FROM shortcuts", null).use { c ->
                while (c.moveToNext()) shorts.getOrPut(c.getString(0)) { HashMap() }[c.getString(1)] = c.getInt(2)
            }
            db.rawQuery("SELECT a,b,n,c FROM trigrams", null).use { c ->
                while (c.moveToNext()) trigrams.getOrPut(c.getString(0) + "\u0001" + c.getString(1)) { HashMap() }[c.getString(2)] = c.getInt(3)
            }
        }
    }

    companion object {
        fun skeleton(s: String): String = s.lowercase().filter { it !in "aeiou" }

        private fun okScript(mode: Int, w: String): Boolean {
            if (w.isEmpty()) return false
            val ascii = w[0].code < 0x80
            return ascii == (mode == MODE_EN)
        }
    }

    private fun inc(m: HashMap<String, Int>, k: String) {
        m[k] = (m[k] ?: 0) + 1
    }

    private fun incNested(m: HashMap<String, HashMap<String, Int>>, a: String, b: String) {
        inc(m.getOrPut(a) { HashMap() }, b)
    }

    private fun put(m: HashMap<String, Double>, k: String, v: Double) {
        if (v > (m[k] ?: 0.0)) m[k] = v
    }

    /** Record a committed word. raw = what the user physically typed for it. */
    fun learn(mode: Int, prev2: String, prev: String, raw: String, word: String) {
        if (word.isEmpty()) return
        val k = raw.lowercase()
        val sk = skeleton(k)
        val keys = ArrayList<String>()
        if (mode != MODE_BN && k.length >= 2) {
            if (k != word.lowercase()) keys.add(k)
            if (sk.length >= 2 && sk != k) keys.add(sk)
        }
        val tkey = if (prev2.isNotEmpty() && prev.isNotEmpty()) prev2 + "\u0001" + prev else ""
        synchronized(lock) {
            inc(words, word)
            if (prev.isNotEmpty()) incNested(bigrams, prev, word)
            if (tkey.isNotEmpty()) incNested(trigrams, tkey, word)
            for (key in keys) incNested(shorts, key, word)
        }
        exec.execute {
            try {
                val db = dbh.writableDatabase
                val now = System.currentTimeMillis()
                db.execSQL("INSERT OR IGNORE INTO words(w,c,t) VALUES(?,0,?)", arrayOf<Any>(word, now))
                db.execSQL("UPDATE words SET c=c+1,t=? WHERE w=?", arrayOf<Any>(now, word))
                if (prev.isNotEmpty()) {
                    db.execSQL("INSERT OR IGNORE INTO bigrams(p,n,c) VALUES(?,?,0)", arrayOf<Any>(prev, word))
                    db.execSQL("UPDATE bigrams SET c=c+1 WHERE p=? AND n=?", arrayOf<Any>(prev, word))
                }
                if (tkey.isNotEmpty()) {
                    db.execSQL("INSERT OR IGNORE INTO trigrams(a,b,n,c) VALUES(?,?,?,0)", arrayOf<Any>(prev2, prev, word))
                    db.execSQL("UPDATE trigrams SET c=c+1 WHERE a=? AND b=? AND n=?", arrayOf<Any>(prev2, prev, word))
                }
                for (key in keys) {
                    db.execSQL("INSERT OR IGNORE INTO shortcuts(k,w,c) VALUES(?,?,0)", arrayOf<Any>(key, word))
                    db.execSQL("UPDATE shortcuts SET c=c+1 WHERE k=? AND w=?", arrayOf<Any>(key, word))
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun rankScore(r: Int): Double = 300.0 / (1.0 + r / 300.0)

    private fun applyCase(raw: String, w: String): String {
        if (raw.isEmpty()) return w
        if (raw.length > 1 && raw.all { it.isUpperCase() || !it.isLetter() }) return w.uppercase()
        if (raw[0].isUpperCase()) return w.replaceFirstChar { it.uppercase() }
        return w
    }

    /**
     * raw = typed characters, display = what is shown in the composing region
     * (for Avro that is the converted Bangla text).
     */
    fun suggest(mode: Int, raw: String, display: String, prev2: String, prev: String, limit: Int): List<String> {
        val scores = HashMap<String, Double>()
        var bg: HashMap<String, Int>? = null
        var tg: HashMap<String, Int>? = null
        synchronized(lock) {
            bg = if (prev.isEmpty()) null else bigrams[prev]?.let { HashMap(it) }
            tg = if (prev2.isEmpty() || prev.isEmpty()) null else trigrams[prev2 + "\u0001" + prev]?.let { HashMap(it) }
        }
        val bgm = bg
        val tgm = tg
        fun bonus(w: String): Double {
            var b = 0.0
            val x = bgm?.get(w)
            if (x != null) b += x * 10.0
            val y = tgm?.get(w)
            if (y != null) b += y * 20.0
            return b
        }
        if (display.isEmpty()) {
            if (tgm != null) for ((n, c) in tgm) if (okScript(mode, n)) put(scores, n, c * 60.0)
            if (bgm != null) for ((n, c) in bgm) if (okScript(mode, n)) put(scores, n, c * 30.0)
            if (scores.size < limit) {
                synchronized(lock) {
                    for ((w, c) in words) if (c >= 3 && okScript(mode, w)) put(scores, w, c * 2.0)
                }
            }
            return scores.entries.sortedByDescending { it.value }.take(limit).map { it.key }
        }

        synchronized(lock) {
            for ((w, c) in words) {
                if (!okScript(mode, w)) continue
                if (w.length > display.length && w.startsWith(display, true)) put(scores, w, 150.0 + c * 60.0 + bonus(w))
            }
            if (mode != MODE_BN) {
                val k = raw.lowercase()
                val sk = skeleton(k)
                for ((key, m) in shorts) {
                    val pref = key.startsWith(k)
                    val skm = sk.length >= 2 && sk != k && key.startsWith(sk)
                    if (!pref && !skm) continue
                    for ((w, c) in m) {
                        if (!okScript(mode, w) || w == display) continue
                        val s = when {
                            key == k -> 800.0 + c * 40
                            pref -> 120.0 + c * 30
                            key == sk -> 500.0 + c * 30
                            else -> 80.0 + c * 20
                        }
                        put(scores, w, s + bonus(w))
                    }
                }
            }
        }

        if (mode == MODE_EN) {
            val lower = display.lowercase()
            val hits = dict.enPrefix(lower, 12)
            for (h in hits) {
                if (h.word == lower) continue
                val cand = applyCase(display, h.word)
                put(scores, cand, 100.0 + rankScore(h.rank) + bonus(cand))
            }
            if (hits.size < 3 && display.length >= 3) {
                for (h in dict.enFuzzy(lower, 4)) {
                    val cand = applyCase(display, h.word)
                    put(scores, cand, 60.0 + rankScore(h.rank) * 0.5 + bonus(cand))
                }
            }
        } else {
            val prefixes = ArrayList<String>()
            prefixes.add(display)
            // word-final "o" is shown as ো while typing; the dictionary word may continue differently
            if (mode == MODE_AVRO && display.endsWith("ো") && raw.endsWith("o")) prefixes.add(display.dropLast(1))
            for (pf in prefixes) {
                for (h in dict.bnPrefix(pf, 12)) {
                    if (h.word == display) continue
                    put(scores, h.word, 100.0 + rankScore(h.rank) + bonus(h.word))
                }
            }
            if (mode == MODE_AVRO) {
                val sk = Phon.latin(raw)
                if (sk.isNotEmpty()) {
                    val f = if (sk.length == 1) 0.3 else 0.7
                    for (h in dict.bnSkelPrefix(sk, 12)) {
                        if (h.word == display) continue
                        put(scores, h.word, 40.0 + rankScore(h.rank) * f + bonus(h.word))
                    }
                }
            }
        }
        return scores.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    /** Forget everything that was learned (clipboard history is kept). */
    fun resetLearning() {
        exec.submit(Runnable { }).get()
        synchronized(lock) {
            words.clear(); bigrams.clear(); trigrams.clear(); shorts.clear()
        }
        try {
            val d = dbh.writableDatabase
            d.execSQL("DELETE FROM words")
            d.execSQL("DELETE FROM bigrams")
            d.execSQL("DELETE FROM trigrams")
            d.execSQL("DELETE FROM shortcuts")
        } catch (e: Exception) {
        }
    }

    fun wordCount(): Int = synchronized(lock) { words.size }

    // ---------- clipboard ----------

    fun addClip(text: String) {
        val s = text.trim()
        if (s.isEmpty() || s.length > 5000) return
        try {
            val d = dbh.writableDatabase
            d.execSQL("DELETE FROM clips WHERE txt=? AND pin=0", arrayOf<Any>(s))
            d.execSQL("INSERT OR IGNORE INTO clips(txt,t,pin) VALUES(?,?,0)", arrayOf<Any>(s, System.currentTimeMillis()))
            d.execSQL("DELETE FROM clips WHERE pin=0 AND id NOT IN (SELECT id FROM clips WHERE pin=0 ORDER BY t DESC LIMIT 100)")
        } catch (e: Exception) {
        }
    }

    fun getClips(): List<Clip> {
        val out = ArrayList<Clip>()
        try {
            dbh.readableDatabase.rawQuery("SELECT id,txt,pin FROM clips ORDER BY pin DESC, t DESC LIMIT 100", null).use { c ->
                while (c.moveToNext()) out.add(Clip(c.getLong(0), c.getString(1), c.getInt(2) == 1))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun deleteClip(id: Long) {
        try {
            dbh.writableDatabase.execSQL("DELETE FROM clips WHERE id=?", arrayOf<Any>(id))
        } catch (e: Exception) {
        }
    }

    fun togglePin(id: Long) {
        try {
            dbh.writableDatabase.execSQL("UPDATE clips SET pin=1-pin WHERE id=?", arrayOf<Any>(id))
        } catch (e: Exception) {
        }
    }

    fun clearClips() {
        try {
            dbh.writableDatabase.execSQL("DELETE FROM clips WHERE pin=0")
        } catch (e: Exception) {
        }
    }

    // ---------- backup / restore ----------

    fun exportJson(): String {
        exec.submit(Runnable { }).get()
        val root = JSONObject()
        root.put("version", 1)
        val db = dbh.readableDatabase
        val w = JSONArray()
        db.rawQuery("SELECT w,c FROM words", null).use { c ->
            while (c.moveToNext()) w.put(JSONArray().put(c.getString(0)).put(c.getInt(1)))
        }
        root.put("words", w)
        val b = JSONArray()
        db.rawQuery("SELECT p,n,c FROM bigrams", null).use { c ->
            while (c.moveToNext()) b.put(JSONArray().put(c.getString(0)).put(c.getString(1)).put(c.getInt(2)))
        }
        root.put("bigrams", b)
        val s = JSONArray()
        db.rawQuery("SELECT k,w,c FROM shortcuts", null).use { c ->
            while (c.moveToNext()) s.put(JSONArray().put(c.getString(0)).put(c.getString(1)).put(c.getInt(2)))
        }
        root.put("shortcuts", s)
        val cl = JSONArray()
        db.rawQuery("SELECT txt,pin FROM clips", null).use { c ->
            while (c.moveToNext()) cl.put(JSONArray().put(c.getString(0)).put(c.getInt(1)))
        }
        root.put("clips", cl)
        return root.toString()
    }

    /** Merge a backup into the current data (keeps the larger count). Returns number of entries processed. */
    fun importJson(text: String): Int {
        exec.submit(Runnable { }).get()
        val root = JSONObject(text)
        var n = 0
        val db = dbh.writableDatabase
        db.beginTransaction()
        try {
            val wa = root.optJSONArray("words")
            if (wa != null) for (i in 0 until wa.length()) {
                val r = wa.getJSONArray(i)
                val w = r.getString(0)
                val c = r.getInt(1)
                val nc = synchronized(lock) {
                    val m = maxOf(words[w] ?: 0, c)
                    words[w] = m
                    m
                }
                db.execSQL("INSERT OR REPLACE INTO words(w,c,t) VALUES(?,?,?)", arrayOf<Any>(w, nc, System.currentTimeMillis()))
                n++
            }
            val ba = root.optJSONArray("bigrams")
            if (ba != null) for (i in 0 until ba.length()) {
                val r = ba.getJSONArray(i)
                val p = r.getString(0)
                val nx = r.getString(1)
                val c = r.getInt(2)
                val nc = synchronized(lock) {
                    val mm = bigrams.getOrPut(p) { HashMap() }
                    val m = maxOf(mm[nx] ?: 0, c)
                    mm[nx] = m
                    m
                }
                db.execSQL("INSERT OR REPLACE INTO bigrams(p,n,c) VALUES(?,?,?)", arrayOf<Any>(p, nx, nc))
                n++
            }
            val sa = root.optJSONArray("shortcuts")
            if (sa != null) for (i in 0 until sa.length()) {
                val r = sa.getJSONArray(i)
                val k = r.getString(0)
                val w = r.getString(1)
                val c = r.getInt(2)
                val nc = synchronized(lock) {
                    val mm = shorts.getOrPut(k) { HashMap() }
                    val m = maxOf(mm[w] ?: 0, c)
                    mm[w] = m
                    m
                }
                db.execSQL("INSERT OR REPLACE INTO shortcuts(k,w,c) VALUES(?,?,?)", arrayOf<Any>(k, w, nc))
                n++
            }
            val ca = root.optJSONArray("clips")
            if (ca != null) for (i in 0 until ca.length()) {
                val r = ca.getJSONArray(i)
                db.execSQL(
                    "INSERT OR IGNORE INTO clips(txt,t,pin) VALUES(?,?,?)",
                    arrayOf<Any>(r.getString(0), System.currentTimeMillis() + i, r.getInt(1))
                )
                n++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return n
    }
}
