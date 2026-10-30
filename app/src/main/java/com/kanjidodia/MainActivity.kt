package com.kanjidodia

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Switch
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AppTheme { MainScreen() } }
    }

    override fun onResume() {
        super.onResume()
        KanjiWidgetProvider.updateAll(this)
    }
}

private val JapaneseStyle = TextStyle(localeList = LocaleList("ja"))

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    var startLevel by remember { mutableIntStateOf(KanjiSchedule.startLevel(context)) }
    var current by remember { mutableStateOf(KanjiSchedule.current(context)) }

    // Troca o kanji na tela quando chega o próximo horário, mesmo com o app aberto
    LaunchedEffect(current.nextChange) {
        val wait = Duration.between(ZonedDateTime.now(), current.nextChange).toMillis()
        delay(wait.coerceAtLeast(0) + 1_000)
        current = KanjiSchedule.current(context)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Kanji do Dia", style = MaterialTheme.typography.headlineMedium)

            KanjiCard(current)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nível JLPT", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (lv in 5 downTo 1) {
                        FilterChip(
                            selected = startLevel == lv,
                            onClick = {
                                if (startLevel != lv) {
                                    KanjiSchedule.setStartLevel(context, lv)
                                    startLevel = lv
                                    current = KanjiSchedule.current(context)
                                    KanjiWidgetProvider.updateAll(context)
                                }
                            },
                            label = { Text("N$lv") },
                        )
                    }
                }
                Text(
                    "Ao terminar um nível, o app avança para o próximo (N5 → N1). " +
                        "Trocar o nível recomeça a sequência a partir dele.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Widget", style = MaterialTheme.typography.titleMedium)
                Text(
                    "O kanji muda a cada hora. Adicione o widget na tela inicial " +
                        "(toque e segure num espaço vazio → Widgets → Kanji do Dia).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val manager = AppWidgetManager.getInstance(context)
                if (manager.isRequestPinAppWidgetSupported) {
                    OutlinedButton(onClick = {
                        manager.requestPinAppWidget(
                            ComponentName(context, KanjiWidgetProvider::class.java), null, null,
                        )
                    }) { Text("Adicionar widget à tela inicial") }
                }
            }

            NotificationSection()

            Text(
                "Dados: KANJIDIC2 e JMdict (EDRDG, CC BY-SA 4.0) e níveis JLPT de kanji-data.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(KanjiNotification.isEnabled(context)) }
    var denied by remember { mutableStateOf(false) }

    fun apply(on: Boolean) {
        enabled = on
        KanjiNotification.setEnabled(context, on)
        KanjiWidgetProvider.updateAll(context)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        apply(granted)
    }
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

    // Na primeira abertura, já pede a permissão para a notificação (ligada por padrão)
    LaunchedEffect(Unit) {
        if (enabled && needsPermission) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Notificação na tela de bloqueio", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Uma notificação fixa e silenciosa com o kanji e a tradução, " +
                        "atualizada a cada hora.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    if (on && needsPermission) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else apply(on)
                },
            )
        }
        if (denied) {
            Text(
                "As notificações estão bloqueadas para o app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }) { Text("Abrir configurações de notificação") }
        }
    }
}

@Composable
private fun KanjiCard(current: CurrentKanji) {
    val k = current.kanji
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "N${k.level} · ${current.position} de ${current.levelSize}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                k.character,
                style = JapaneseStyle.copy(fontSize = 120.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                k.meanings.joinToString(", "),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(16.dp))
            ReadingRow("音読み", k.onReadings)
            Spacer(Modifier.height(8.dp))
            ReadingRow("訓読み", k.kunReadings)
            if (k.examples.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Exemplos",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                for (ex in k.examples) {
                    Spacer(Modifier.height(8.dp))
                    ExampleRow(ex)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Próximo kanji às %02d:00".format(current.nextChange.hour),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadingRow(label: String, readings: List<String>) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = JapaneseStyle.merge(MaterialTheme.typography.labelLarge),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(64.dp),
        )
        Column {
            Text(readings.joinOrDash(), style = JapaneseStyle.copy(fontSize = 18.sp))
            if (readings.isNotEmpty()) {
                Text(
                    readings.joinToString(", ") { Romaji.of(it) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExampleRow(ex: Example) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(ex.word, style = JapaneseStyle.copy(fontSize = 22.sp))
            Spacer(Modifier.width(8.dp))
            Text(
                "${ex.reading} · ${Romaji.of(ex.reading)}",
                style = JapaneseStyle.merge(MaterialTheme.typography.bodyMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(ex.meaning, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFFFB4AB))
        else -> lightColorScheme(primary = Color(0xFFB3261E))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
