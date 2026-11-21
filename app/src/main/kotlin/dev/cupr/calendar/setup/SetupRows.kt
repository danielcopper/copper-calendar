package dev.cupr.calendar.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cupr.calendar.R
import dev.cupr.calendar.app.ROW_HEIGHT
import dev.cupr.calendar.app.RadioOptions
import dev.cupr.calendar.ha.SensorCalendar
import dev.cupr.calendar.store.CardOpacity
import kotlin.math.roundToInt

/** A setting's row: its name and its value; onClick, when given, opens its dialog. */
@Composable
internal fun SettingRow(
    name: String,
    value: String,
    onClick: (() -> Unit)?,
) {
    val tappable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_HEIGHT)
                .then(tappable)
                // Read as one, name and value, also when it opens nothing.
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A setting of one of options, each named by label: a row with the one selected that opens their dialog. */
@Composable
internal fun <T> ChoiceRow(
    @StringRes name: Int,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingRow(stringResource(name), stringResource(label(selected))) { open = true }
    if (open) {
        ChoiceDialog(name, options, selected, label, onDismiss = { open = false }) {
            open = false
            onSelect(it)
        }
    }
}

/** options under title, the one selected marked; picking one hands it to onPick, "Abbrechen" to onDismiss. */
@Composable
internal fun <T> ChoiceDialog(
    @StringRes title: Int,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { Cancel(onDismiss) },
        title = { Text(stringResource(title)) },
        text = { RadioOptions(options, selected, label, onPick) },
    )
}

/**
 * The calendars a widget shows (D11): a row saying all, or how many of
 * those listed, that opens a box per calendar; "OK" hands the calendars left
 * out to onChange. With none known the row says so and opens nothing.
 */
@Composable
internal fun CalendarsRow(
    calendars: List<SensorCalendar>,
    leftOut: Set<String>,
    onChange: (Set<String>) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val shown = calendars.count { it.id !in leftOut }
    val value =
        when {
            calendars.isEmpty() -> stringResource(R.string.setup_no_calendars)
            shown == calendars.size -> stringResource(R.string.setup_calendars_all)
            else -> pluralStringResource(R.plurals.setup_calendars_some, shown, shown, calendars.size)
        }
    SettingRow(stringResource(R.string.setup_calendars), value, if (calendars.isEmpty()) null else ({ open = true }))
    if (open) {
        CalendarsDialog(calendars, leftOut, onDismiss = { open = false }) {
            open = false
            onChange(it)
        }
    }
}

/**
 * A box per calendar, named by its name, else its id, ticked unless left
 * out; "OK" hands the calendars then left out to onDone, "Abbrechen" keeps
 * leftOut.
 */
@Composable
private fun CalendarsDialog(
    calendars: List<SensorCalendar>,
    leftOut: Set<String>,
    onDismiss: () -> Unit,
    onDone: (Set<String>) -> Unit,
) {
    var unticked by rememberSaveable { mutableStateOf<List<String>>(leftOut.toList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onDone(unticked.toSet()) }) { Text(stringResource(R.string.setup_ok)) }
        },
        dismissButton = { Cancel(onDismiss) },
        title = { Text(stringResource(R.string.setup_calendars)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (listed in calendars) {
                    val calendar = listed.id
                    val ticked = calendar !in unticked
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = ROW_HEIGHT)
                                .toggleable(value = ticked, role = Role.Checkbox) {
                                    unticked = if (it) unticked - calendar else unticked + calendar
                                },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = ticked, onCheckedChange = null)
                        Text(listed.shown, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
    )
}

/**
 * The card's opacity (D64): a row showing it in percent that opens a slider
 * from 0 to 100 % in steps of 10, its value beside it. Unlike a choice, the
 * slider is not taken as it moves: "OK" hands its value to onChange,
 * "Abbrechen" keeps opacity.
 */
@Composable
internal fun OpacityRow(
    opacity: Int,
    onChange: (Int) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingRow(stringResource(R.string.setup_opacity), percent(opacity)) { open = true }
    if (open) {
        OpacityDialog(opacity, onDismiss = { open = false }) {
            open = false
            onChange(it)
        }
    }
}

@Composable
private fun OpacityDialog(
    opacity: Int,
    onDismiss: () -> Unit,
    onDone: (Int) -> Unit,
) {
    var shown by rememberSaveable { mutableIntStateOf(opacity) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onDone(shown) }) { Text(stringResource(R.string.setup_ok)) }
        },
        dismissButton = { Cancel(onDismiss) },
        title = { Text(stringResource(R.string.setup_opacity)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = shown.toFloat(),
                    onValueChange = { shown = (it / CardOpacity.STEP).roundToInt() * CardOpacity.STEP },
                    modifier = Modifier.weight(1f),
                    valueRange = 0f..CardOpacity.FULL.toFloat(),
                    // The steps between the ends: 10 % to 90 %.
                    steps = CardOpacity.steps.size - 2,
                )
                // As wide as "100 %", so the slider keeps its width as the value changes.
                Text(
                    percent(shown),
                    modifier = Modifier.padding(start = 16.dp).widthIn(min = PERCENT_WIDTH),
                    textAlign = TextAlign.End,
                )
            }
        },
    )
}

/** An opacity as the setup shows it, "50 %". */
@Composable
private fun percent(opacity: Int): String = stringResource(R.string.setup_opacity_percent, opacity)

/** The room the opacity's value takes beside its slider, enough for "100 %". */
private val PERCENT_WIDTH = 48.dp

/** A dialog's "Abbrechen", which closes it without a change. */
@Composable
internal fun Cancel(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(R.string.setup_cancel)) }
}
