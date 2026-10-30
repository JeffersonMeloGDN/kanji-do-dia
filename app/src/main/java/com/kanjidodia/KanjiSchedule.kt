package com.kanjidodia

import android.content.Context
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlin.random.Random

data class CurrentKanji(
    val kanji: Kanji,
    /** Posição (1-based) do kanji dentro do seu nível. */
    val position: Int,
    val levelSize: Int,
    val nextChange: ZonedDateTime,
)

/**
 * Cada hora cheia (no horário local) recebe um kanji.
 *
 * A sequência é determinística: a partir do nível escolhido, os kanjis de cada nível são
 * embaralhados com uma semente fixa e percorridos em ordem (N5 → N4 → ... → N1). Assim o
 * kanji atual depende apenas de quantas horas se passaram desde a escolha do nível, e
 * nenhum alarme atrasado ou perdido faz o app pular ou repetir um kanji.
 */
object KanjiSchedule {
    private const val PREFS = "kanji_schedule"
    private const val KEY_LEVEL = "start_level"
    private const val KEY_ANCHOR = "anchor_slot"
    private const val KEY_SEED = "seed"
    private const val KEY_HOURLY = "hourly_slots"

    fun startLevel(context: Context): Int = prefs(context).getInt(KEY_LEVEL, 5)

    /** Escolhe um novo nível inicial; a sequência recomeça no período atual. */
    fun setStartLevel(context: Context, level: Int) {
        prefs(context).edit()
            .putInt(KEY_LEVEL, level)
            .putLong(KEY_ANCHOR, slotId(LocalDateTime.now()))
            .putLong(KEY_SEED, Random.nextLong())
            .putBoolean(KEY_HOURLY, true)
            .apply()
    }

    fun current(context: Context, now: ZonedDateTime = ZonedDateTime.now()): CurrentKanji {
        val p = prefs(context)
        if (!p.contains(KEY_ANCHOR)) setStartLevel(context, p.getInt(KEY_LEVEL, 5))
        if (!p.getBoolean(KEY_HOURLY, false)) migrateToHourly(context, now.toLocalDateTime())

        val startLevel = p.getInt(KEY_LEVEL, 5)
        val seed = p.getLong(KEY_SEED, 0L)
        val offset = (slotId(now.toLocalDateTime()) - p.getLong(KEY_ANCHOR, 0L)).coerceAtLeast(0L)

        val levels = (startLevel downTo 1).map { KanjiRepository.level(context, it) }
        val total = levels.sumOf { it.size }
        // Depois do N1, recomeça no nível escolhido com uma nova ordem
        val cycle = offset / total
        var pos = (offset % total).toInt()
        for (list in levels) {
            if (pos < list.size) {
                val lv = list.first().level
                val order = list.indices.shuffled(Random(seed xor (lv * 1_000_003L) xor (cycle * 7_919L)))
                return CurrentKanji(list[order[pos]], pos + 1, list.size, nextChange(now))
            }
            pos -= list.size
        }
        error("posição fora da sequência")
    }

    /** Índice global da hora (horário local). */
    fun slotId(time: LocalDateTime): Long = time.toLocalDate().toEpochDay() * 24 + time.hour

    fun nextChange(now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().atTime(now.hour, 0).plusHours(1).atZone(now.zone)

    /**
     * A versão anterior trocava 3 vezes ao dia (8h, 14h, 20h). Converte a âncora para horas
     * mantendo a posição atual na sequência, para não perder o progresso.
     */
    private fun migrateToHourly(context: Context, now: LocalDateTime) {
        val p = prefs(context)
        val shifted = now.minusHours(8)
        val oldSlot = shifted.toLocalDate().toEpochDay() * 3 +
            intArrayOf(8, 14, 20).indexOfLast { shifted.hour + 8 >= it }
        val position = (oldSlot - p.getLong(KEY_ANCHOR, oldSlot)).coerceAtLeast(0L)
        p.edit()
            .putLong(KEY_ANCHOR, slotId(now) - position)
            .putBoolean(KEY_HOURLY, true)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
