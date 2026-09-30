package com.openfit.mobile.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openfit.mobile.data.exercise.HC_EXERCISE_TYPES
import kotlinx.coroutines.delay

/**
 * Searchable/filterable dropdown for selecting a Health Connect activity type.
 *
 * Internal [query] state is intentionally decoupled from [selected]: using
 * `remember(selected)` would re-create the mutable state on every [onSelect]
 * call, resetting the text field's internal cursor/composition after each
 * keystroke and making deletion feel broken. Instead the state lives purely
 * inside this composable and is only synchronised outward via [onSelect].
 *
 * In [allowFreeform] mode [onSelect] is debounced 300 ms so rapid deletion
 * doesn't flood the parent with intermediate partial strings.
 *
 * @param selected      currently displayed label (used for initialisation only).
 * @param onSelect      called with the chosen or typed label.
 * @param allowFreeform if true (Activity rename), user may type a completely
 *                      custom label — [onSelect] fires after a 300 ms pause.
 *                      if false (Quick Log), only picking from the list commits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseTypeDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowFreeform: Boolean = false,
    label: String = "Activity type",
) {
    // Pure internal state — never re-keyed on [selected].
    // Avoids cursor-reset feedback loop when parent echoes typed text back.
    var query by remember { mutableStateOf(selected) }
    var expanded by remember { mutableStateOf(false) }

    val filtered = remember(query) {
        if (query.isBlank()) HC_EXERCISE_TYPES
        else HC_EXERCISE_TYPES.filter { it.label.contains(query, ignoreCase = true) }
    }

    // Debounced freeform notification: fires 300 ms after typing stops.
    if (allowFreeform) {
        LaunchedEffect(query) {
            delay(300)
            onSelect(query)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { q ->
                query = q
                expanded = true
                // Immediate feedback in non-freeform mode is unneeded here;
                // freeform notification goes through the debounced effect above.
            },
            label = { Text(label) },
            placeholder = { Text("Type to filter…") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded && filtered.isNotEmpty()
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )

        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 260.dp),
        ) {
            filtered.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        query = type.label
                        onSelect(type.label)   // immediate on explicit selection
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
