package com.kanjidodia

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Após reiniciar, atualizar o app ou mudar hora/fuso, o alarme precisa ser refeito. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        KanjiWidgetProvider.updateAll(context)
    }
}
