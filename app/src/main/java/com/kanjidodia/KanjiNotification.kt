package com.kanjidodia

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Notificação fixa e silenciosa com o kanji atual, visível na tela de bloqueio. */
object KanjiNotification {
    private const val CHANNEL_ID = "kanji_atual"
    private const val NOTIFICATION_ID = 1
    private const val PREFS = "kanji_settings"
    private const val KEY_ENABLED = "notification_enabled"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Publica (ou substitui) a notificação. Retorna false se estiver desligada ou sem permissão. */
    fun show(context: Context, current: CurrentKanji): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!isEnabled(context) || !manager.areNotificationsEnabled()) return false
        createChannel(context)

        val k = current.kanji
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = "${k.meanings.joinToString(", ")} — ${k.pronunciation}"
        // Expandida, a notificação também mostra as palavras de exemplo
        val expanded = (listOf(text) + k.examples.map {
            "${it.word} (${Romaji.of(it.reading)}): ${it.meaning}"
        }).joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(japanese(k.character))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(japanese(expanded)))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            return false
        }
        return true
    }

    private fun createChannel(context: Context) {
        // Importância padrão (sem som/vibração) em vez de "baixa": canais baixos contam como
        // notificações silenciosas, que muitos celulares escondem na tela de bloqueio.
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
