package com.mykb.keyboard

import android.content.Context

class Emo(val e: String, val skins: List<String>)

/** Complete Unicode emoji list (bundled asset), grouped: smileys, people, animals, food, travel, activities, objects, symbols, flags. */
object EmojiData {
    @Volatile
    private var cache: List<List<Emo>>? = null

    fun groups(ctx: Context): List<List<Emo>> {
        val c = cache
        if (c != null) return c
        val out = ArrayList<ArrayList<Emo>>()
        try {
            ctx.assets.open("emoji.txt").bufferedReader(Charsets.UTF_8).use { r ->
                var s = r.readLine()
                while (s != null) {
                    if (s.startsWith("#")) {
                        out.add(ArrayList())
                    } else if (s.isNotEmpty() && out.isNotEmpty()) {
                        val p = s.split("\t")
                        val skins = if (p.size > 1 && p[1].isNotBlank()) p[1].trim().split(" ") else emptyList()
                        out[out.size - 1].add(Emo(p[0], skins))
                    }
                    s = r.readLine()
                }
            }
        } catch (e: Exception) {
        }
        cache = out
        return out
    }
}
