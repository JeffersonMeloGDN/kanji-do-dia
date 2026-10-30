package com.kanjidodia

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LocaleSpan
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import java.util.Locale

class KanjiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) updateAll(context)
    }

    override fun onDisabled(context: Context) {
        updateAll(context)
    }

    companion object {
        private const val ACTION_TICK = "com.kanjidodia.TICK"

        /**
         * Atualiza os widgets e a notificação fixa e agenda a próxima troca (na próxima
         * hora cheia). Sem widget e sem notificação, o alarme é cancelado.
         */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, KanjiWidgetProvider::class.java))
            val alarms = context.getSystemService(AlarmManager::class.java)

            val current = KanjiSchedule.current(context)
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, buildViews(context, current))
            val notified = KanjiNotification.show(context, current)
            if (ids.isEmpty() && !notified) {
                alarms.cancel(tickIntent(context))
                return
            }

            // Alarme não-wakeup: se o celular estiver dormindo, dispara assim que acordar
            alarms.setWindow(
                AlarmManager.RTC,
                current.nextChange.toInstant().toEpochMilli(),
                5 * 60_000L,
                tickIntent(context),
            )
        }

        /**
         * Tamanhos de texto por espaço disponível (dp). Na tela de bloqueio do Android 16
         * o widget ocupa ~4×3 células, então o kanji precisa crescer junto.
         */
        private val SIZES = listOf(
            SizeF(80f, 80f) to (56f to 14f),
            SizeF(150f, 150f) to (88f to 18f),
            SizeF(250f, 200f) to (128f to 22f),
        )

        private fun buildViews(context: Context, current: CurrentKanji): RemoteViews {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return buildViews(context, current, kanjiSp = 64f, meaningSp = 16f)
            }
            // O sistema escolhe a variante que cabe no tamanho atual do widget
            return RemoteViews(SIZES.associate { (size, sp) ->
                size to buildViews(context, current, sp.first, sp.second)
            })
        }

        private fun buildViews(
            context: Context, current: CurrentKanji, kanjiSp: Float, meaningSp: Float,
        ): RemoteViews {
            val k = current.kanji
            return RemoteViews(context.packageName, R.layout.widget_kanji).apply {
                setTextViewText(R.id.widget_kanji, japanese(k.character))
                setTextViewTextSize(R.id.widget_kanji, TypedValue.COMPLEX_UNIT_SP, kanjiSp)
                setTextViewText(R.id.widget_meaning, k.meanings.joinToString(", "))
                setTextViewTextSize(R.id.widget_meaning, TypedValue.COMPLEX_UNIT_SP, meaningSp)
                setTextViewText(R.id.widget_reading, k.pronunciation)
                setTextViewTextSize(R.id.widget_reading, TypedValue.COMPLEX_UNIT_SP, meaningSp * 0.8f)
                val open = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                setOnClickPendingIntent(R.id.widget_root, open)
            }
        }

        private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, KanjiWidgetProvider::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Garante a forma japonesa do glifo mesmo com o celular em português. */
fun japanese(text: String): CharSequence = SpannableString(text).apply {
    setSpan(LocaleSpan(Locale.JAPANESE), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

fun List<String>.joinOrDash(): String = if (isEmpty()) "—" else joinToString("、")
