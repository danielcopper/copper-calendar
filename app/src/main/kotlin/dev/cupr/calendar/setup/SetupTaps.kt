package dev.cupr.calendar.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.cupr.calendar.R
import dev.cupr.calendar.app.Heading
import dev.cupr.calendar.app.ROW_HEIGHT
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.store.EntryTarget
import dev.cupr.calendar.store.PartTarget
import dev.cupr.calendar.store.TapTarget
import dev.cupr.calendar.store.TapTargets
import dev.cupr.calendar.tap.TapElement
import dev.cupr.calendar.tap.of
import dev.cupr.calendar.widget.WidgetKind
import kotlinx.coroutines.withContext

/** The elements whose taps a widget of kind sets, in the setup's order: an entry only where it has entry areas. */
internal val WidgetKind.tapElements: List<TapElement>
    get() = TapElement.entries.filter { it != TapElement.ENTRY || entryTaps }

/**
 * What a widget's taps open (D63), a group of its own below a divider: a
 * row per element of kind, indented below the heading, naming its target; a
 * row opens the choice of targets.
 */
@Composable
internal fun TapSection(
    kind: WidgetKind,
    taps: TapTargets,
    onChange: (TapTargets) -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf<TapElement?>(null) }
    var pickingApp by rememberSaveable { mutableStateOf<TapElement?>(null) }
    Column {
        HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
        Heading(R.string.setup_taps)
        Column(modifier = Modifier.padding(start = TAP_ROWS_INDENT)) {
            for (element in kind.tapElements) {
                SettingRow(stringResource(element.label), targetName(element, taps.of(element))) { choosing = element }
            }
        }
    }
    choosing?.let { element ->
        ChoiceDialog(
            title = element.label,
            options = Pick.options(element),
            selected = Pick.of(taps.of(element)),
            label = { it.label(element) },
            onDismiss = { choosing = null },
        ) { pick ->
            choosing = null
            val target = pick.target
            if (target == null) pickingApp = element else taps.with(element, target)?.let(onChange)
        }
    }
    pickingApp?.let { element ->
        AppDialog(onDismiss = { pickingApp = null }) { app ->
            pickingApp = null
            taps.with(element, TapTarget.App(app))?.let(onChange)
        }
    }
}

/** The name of target as element's row shows it: another app by its name, or that it is gone. */
@Composable
private fun targetName(
    element: TapElement,
    target: TapTarget,
): String {
    if (target !is TapTarget.App) return stringResource(Pick.of(target).label(element))
    val packageManager = LocalContext.current.packageManager
    val name by produceState<String?>(null, target) {
        value = withContext(Services.io) { launcherName(packageManager, target.packageName) }
    }
    return name ?: stringResource(R.string.setup_tap_app_missing)
}

/** The apps with a launcher icon, by icon and name; picking one hands its package to onPick. */
@Composable
private fun AppDialog(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val packageManager = LocalContext.current.packageManager
    val apps by produceState<List<LauncherApp>?>(null) {
        value = withContext(Services.io) { launcherApps(packageManager) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { Cancel(onDismiss) },
        title = { Text(stringResource(R.string.setup_tap_apps)) },
        text = {
            val listed = apps
            if (listed == null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn {
                    items(listed, key = { it.packageName }) { app -> AppRow(app) { onPick(app.packageName) } }
                }
            }
        },
    )
}

@Composable
private fun AppRow(
    app: LauncherApp,
    onClick: () -> Unit,
) {
    val packageManager = LocalContext.current.packageManager
    val size = with(LocalDensity.current) { ICON_SIZE.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, app) {
        value =
            withContext(Services.io) {
                launcherIcon(packageManager, app)?.toBitmap(size, size)?.asImageBitmap()
            }
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_HEIGHT)
                .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(ICON_SIZE)) }
            ?: Spacer(modifier = Modifier.size(ICON_SIZE))
        Text(app.name, modifier = Modifier.padding(start = 16.dp))
    }
}

private val ICON_SIZE = 40.dp

/** How far the rows of the taps stand in from their heading. */
private val TAP_ROWS_INDENT = 16.dp

@get:StringRes
private val TapElement.label: Int
    get() =
        when (this) {
            TapElement.HEADER -> R.string.setup_tap_header
            TapElement.DAY -> R.string.setup_tap_day
            TapElement.ENTRY -> R.string.setup_tap_entry
            TapElement.NEW -> R.string.setup_tap_new
        }

/** A choice in an element's dialog, its [target]; another app has none until its own list picks one. */
private enum class Pick(
    val target: TapTarget?,
) {
    CALENDAR(TapTarget.CalendarPage),
    ENTRY_DAY(TapTarget.EntryDay),
    OTHER_APP(null),
    NOTHING(TapTarget.None),
    ;

    @StringRes
    fun label(element: TapElement): Int =
        when (this) {
            CALENDAR -> calendarLabel(element)
            ENTRY_DAY -> R.string.setup_tap_entry_day
            OTHER_APP -> R.string.setup_tap_other_app
            NOTHING -> if (element == TapElement.NEW) R.string.setup_tap_nothing_new else R.string.setup_tap_nothing
        }

    companion object {
        /** What element's dialog offers: an entry its day instead of another app (D63). */
        fun options(element: TapElement): List<Pick> =
            listOf(CALENDAR, if (element == TapElement.ENTRY) ENTRY_DAY else OTHER_APP, NOTHING)

        fun of(target: TapTarget): Pick =
            if (target is TapTarget.App) OTHER_APP else entries.first { it.target == target }
    }
}

/** What a tap on element opens on the calendar page, as a dialog and a row name it. */
@StringRes
private fun calendarLabel(element: TapElement): Int =
    when (element) {
        TapElement.HEADER -> R.string.setup_tap_calendar
        TapElement.DAY -> R.string.setup_tap_calendar_day
        TapElement.ENTRY -> R.string.setup_tap_calendar_entry
        TapElement.NEW -> R.string.setup_tap_calendar_new
    }

/** taps with element's target set to target; none when element cannot have it (D63). */
internal fun TapTargets.with(
    element: TapElement,
    target: TapTarget,
): TapTargets? =
    when (element) {
        TapElement.ENTRY -> (target as? EntryTarget)?.let { copy(entry = it) }
        TapElement.HEADER -> (target as? PartTarget)?.let { copy(header = it) }
        TapElement.DAY -> (target as? PartTarget)?.let { copy(day = it) }
        TapElement.NEW -> (target as? PartTarget)?.let { copy(new = it) }
    }
