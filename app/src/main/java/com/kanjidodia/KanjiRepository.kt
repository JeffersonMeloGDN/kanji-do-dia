package com.kanjidodia

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Kanji(
    val character: String,
    val level: Int,
    val meanings: List<String>,
    val onReadings: List<String>,
    val kunReadings: List<String>,
    val examples: List<Example>,
) {
    /** Leituras principais em rōmaji, ex.: "nichi, jitsu · hi, -bi". */
    val pronunciation: String
        get() = listOf(onReadings, kunReadings)
            .filter { it.isNotEmpty() }
            .joinToString(" · ") { rs -> rs.take(2).joinToString(", ") { Romaji.of(it) } }
}

/** Palavra de exemplo que usa o kanji, ex.: 毎日 (まいにち) "todo dia". */
data class Example(val word: String, val reading: String, val meaning: String)

/** Kanjis por nível JLPT (5 = N5 ... 1 = N1), lidos de assets/kanji.json. */
object KanjiRepository {
    @Volatile
    private var cache: Map<Int, List<Kanji>>? = null

    fun level(context: Context, level: Int): List<Kanji> = load(context).getValue(level)

    private fun load(context: Context): Map<Int, List<Kanji>> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val text = context.applicationContext.assets.open("kanji.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(text)
            val result = (1..5).associateWith { lv ->
                val arr = root.getJSONArray("N$lv")
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    Kanji(
                        character = o.getString("k"),
                        level = lv,
                        meanings = o.getJSONArray("pt").toStringList(),
                        onReadings = o.getJSONArray("on").toStringList(),
                        kunReadings = o.getJSONArray("kun").toStringList(),
                        examples = o.optJSONArray("ex")?.let { ex ->
                            List(ex.length()) { j ->
                                val e = ex.getJSONArray(j)
                                Example(e.getString(0), e.getString(1), e.getString(2))
                            }
                        }.orEmpty(),
                    )
                }
            }
            cache = result
            return result
        }
    }

    private fun JSONArray.toStringList(): List<String> = List(length()) { getString(it) }
}
