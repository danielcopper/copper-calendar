package dev.cupr.calendar.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The least height of a row that taps, Material's touch target. */
internal val ROW_HEIGHT = 48.dp

/** One of options under title, each named by label; selecting one hands it to onSelect. */
@Composable
internal fun <T> Choice(
    @StringRes title: Int,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    Column {
        Heading(title)
        RadioOptions(options, selected, label, onSelect)
    }
}

/** A radio button per option, named by label, the selected one on; selecting one hands it to onSelect. */
@Composable
internal fun <T> RadioOptions(
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        for (option in options) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ROW_HEIGHT)
                        .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Text(stringResource(label(option)), modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
internal fun Heading(
    @StringRes text: Int,
) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
