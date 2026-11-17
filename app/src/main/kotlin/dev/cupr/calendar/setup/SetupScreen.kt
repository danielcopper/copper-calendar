package dev.cupr.calendar.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.cupr.calendar.R
import dev.cupr.calendar.app.Choice
import dev.cupr.calendar.app.Heading
import dev.cupr.calendar.app.ROW_HEIGHT
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.app.Settings
import dev.cupr.calendar.ha.SensorCalendar
import dev.cupr.calendar.ha.SensorCalendars
import dev.cupr.calendar.store.BackToToday
import dev.cupr.calendar.store.PlusCorner
import dev.cupr.calendar.store.WidgetSettings
import dev.cupr.calendar.store.stored
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.widget.WidgetKind
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * The setup of the widget id, a widget of kind: the calendars it shows
 * (D11), the week widgets' days (D20), when the month and "Woche" return to
 * today (D27), and the corner of "+". Its stored settings and the calendars
 * are read off the main thread; "Speichern" stores the settings shown and
 * redraws the widget with them ([save]), then calls onDone with whether it
 * did.
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
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                stringResource(R.string.setup_title, stringResource(kind.label)),
                style = MaterialTheme.typography.headlineSmall,
            )
            val current = settings ?: return@Column
            Calendars(calendars ?: return@Column, current.leftOut) { settings = current.copy(leftOut = it) }
            if (kind.days) {
                Choice(R.string.setup_days, WeekDays.entries, current.days, ::daysLabel) {
                    settings = current.copy(days = it)
                }
            }
            if (kind.backToToday) {
                Choice(R.string.setup_back_to_today, BackToToday.entries, current.backToToday, ::backLabel) {
                    settings = current.copy(backToToday = it)
                }
            }
            Choice(R.string.setup_plus_corner, PlusCorner.entries, current.plusCorner, ::cornerLabel) {
                settings = current.copy(plusCorner = it)
            }
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

/**
 * A box per calendar, named by its name, else its id, ticked unless it is in
 * leftOut; a change hands the new calendars left out to onChange.
 */
@Composable
private fun Calendars(
    calendars: List<SensorCalendar>,
    leftOut: Set<String>,
    onChange: (Set<String>) -> Unit,
) {
    Column {
        Heading(R.string.setup_calendars)
        if (calendars.isEmpty()) Text(stringResource(R.string.setup_no_calendars))
        for (listed in calendars) {
            val calendar = listed.id
            val ticked = calendar !in leftOut
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ROW_HEIGHT)
                        .toggleable(
                            value = ticked,
                            role = Role.Checkbox,
                            onValueChange = { onChange(if (it) leftOut - calendar else leftOut + calendar) },
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = ticked, onCheckedChange = null)
                Text(listed.shown, modifier = Modifier.padding(start = 16.dp))
            }
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
                ?.let { listOf(it.days.stored, it.backToToday.stored, it.plusCorner.stored) + it.leftOut }
                .orEmpty()
        },
        restore = { saved ->
            val days = WeekDays.entries.firstOrNull { it.stored == saved.getOrNull(0) }
            val back = BackToToday.entries.firstOrNull { it.stored == saved.getOrNull(1) }
            val corner = PlusCorner.entries.firstOrNull { it.stored == saved.getOrNull(2) }
            if (days == null || back == null || corner == null) {
                null
            } else {
                WidgetSettings(saved.drop(SAVED_CHOICES).toSet(), days, back, corner)
            }
        },
    )

/** The choices [SettingsSaver] saves before the calendars left out. */
private const val SAVED_CHOICES = 3
