package dev.cupr.calendar.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.cupr.calendar.R
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.app.Settings
import dev.cupr.calendar.ha.SensorCalendar
import dev.cupr.calendar.ha.SensorCalendars
import dev.cupr.calendar.store.BackToToday
import dev.cupr.calendar.store.PlusCorner
import dev.cupr.calendar.store.TapTarget
import dev.cupr.calendar.store.TapTargets
import dev.cupr.calendar.store.WidgetSettings
import dev.cupr.calendar.store.stored
import dev.cupr.calendar.tap.TapElement
import dev.cupr.calendar.tap.of
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.widget.WidgetKind
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * The setup of the widget id, a widget of kind: the calendars it shows
 * (D11), the week widgets' days (D20), when the month and "Woche" return to
 * today (D27), the corner of "+", and what a tap on each element opens
 * (D63). Its stored settings and the calendars are read off the main
 * thread; "Speichern" stores the settings shown and redraws the widget with
 * them ([save]), then calls onDone with whether it did.
 */
@Composable
fun SetupScreen(
    id: Int,
    kind: WidgetKind,
    onDone: (saved: Boolean) -> Unit,
) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var settings by rememberSaveable(stateSaver = SettingsSaver) { mutableStateOf<WidgetSettings?>(null) }
    var calendars by remember { mutableStateOf<List<SensorCalendar>?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        val restored = settings
        val (stored, listed) =
            withContext(Services.io) {
                val kept = Settings.widgets(app).read(id)
                val snapshot = Services.snapshots(app).read()
                val entries = snapshot?.parse(ZoneId.systemDefault())?.entries.orEmpty()
                // Its problems were logged by the fetch that stored it.
                val sensor = SensorCalendars.read(snapshot?.calendars).calendars
                kept to setupCalendars(sensor, entries, kept.leftOut + restored?.leftOut.orEmpty())
            }
        settings = restored ?: stored
        calendars = listed
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.setup_title, stringResource(kind.label)),
                style = MaterialTheme.typography.headlineSmall,
            )
            val current = settings ?: return@Column
            val listed = calendars ?: return@Column
            SettingRows(kind, listed, current) { settings = it }
            TapSection(kind, current.taps) { settings = current.copy(taps = it) }
            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch { onDone(withContext(Services.io) { save(app, id, kind, current) }) }
                },
            ) { Text(stringResource(R.string.setup_save)) }
        }
    }
}

/** A row per setting of kind besides the taps, each with its value, opening its dialog; a change goes to onChange. */
@Composable
private fun SettingRows(
    kind: WidgetKind,
    calendars: List<SensorCalendar>,
    current: WidgetSettings,
    onChange: (WidgetSettings) -> Unit,
) {
    Column {
        CalendarsRow(calendars, current.leftOut) { onChange(current.copy(leftOut = it)) }
        if (kind.days) {
            ChoiceRow(R.string.setup_days, WeekDays.entries, current.days, ::daysLabel) {
                onChange(current.copy(days = it))
            }
        }
        if (kind.backToToday) {
            ChoiceRow(R.string.setup_back_to_today, BackToToday.entries, current.backToToday, ::backLabel) {
                onChange(current.copy(backToToday = it))
            }
        }
        ChoiceRow(R.string.setup_plus_corner, PlusCorner.entries, current.plusCorner, ::cornerLabel) {
            onChange(current.copy(plusCorner = it))
        }
    }
}

@StringRes
private fun daysLabel(days: WeekDays): Int =
    when (days) {
        WeekDays.MONDAY_TO_SUNDAY -> R.string.setup_days_monday_to_sunday
        WeekDays.FROM_TODAY -> R.string.setup_days_from_today
    }

@StringRes
private fun backLabel(back: BackToToday): Int =
    when (back) {
        BackToToday.EACH_UPDATE -> R.string.setup_back_each_update
        BackToToday.MIDNIGHT -> R.string.setup_back_midnight
        BackToToday.NEVER -> R.string.setup_back_never
        BackToToday.EVERY_15_MINUTES -> R.string.every_15_minutes
        BackToToday.EVERY_30_MINUTES -> R.string.every_30_minutes
        BackToToday.EVERY_60_MINUTES -> R.string.every_60_minutes
    }

@StringRes
private fun cornerLabel(corner: PlusCorner): Int =
    when (corner) {
        PlusCorner.BOTTOM_RIGHT -> R.string.setup_plus_bottom_right
        PlusCorner.BOTTOM_LEFT -> R.string.setup_plus_bottom_left
    }

/**
 * Keeps the settings shown, changed or not, across a configuration change,
 * by the names the store keeps them under; a name it does not know restores
 * none, and the screen reads the stored settings again.
 */
internal val SettingsSaver =
    listSaver<WidgetSettings?, String>(
        save = { settings ->
            settings
                ?.let {
                    val taps = it.taps
                    listOf(it.days.stored, it.backToToday.stored, it.plusCorner.stored) +
                        TapElement.entries.map { element -> taps.of(element).stored } +
                        it.leftOut
                }.orEmpty()
        },
        restore = { saved ->
            val days = WeekDays.entries.firstOrNull { it.stored == saved.getOrNull(0) }
            val back = BackToToday.entries.firstOrNull { it.stored == saved.getOrNull(1) }
            val corner = PlusCorner.entries.firstOrNull { it.stored == saved.getOrNull(2) }
            if (days == null || back == null || corner == null) {
                null
            } else {
                restoreTaps(saved)?.let { WidgetSettings(saved.drop(SAVED_CHOICES).toSet(), days, back, corner, it) }
            }
        },
    )

/** The tap targets [SettingsSaver] saved after the three choices, by element; none if one is unknown or misplaced. */
private fun restoreTaps(saved: List<String>): TapTargets? =
    TapElement.entries.fold<TapElement, TapTargets?>(TapTargets.DEFAULT) { taps, element ->
        saved.getOrNull(FIRST_TAP + element.ordinal)?.let(TapTarget::of)?.let { taps?.with(element, it) }
    }

/** Where [SettingsSaver] saves the tap targets, after the days, "Zurück zu heute" and the corner of "+". */
private const val FIRST_TAP = 3

/** The choices [SettingsSaver] saves before the calendars left out: three, then the four tap targets. */
private const val SAVED_CHOICES = FIRST_TAP + 4
