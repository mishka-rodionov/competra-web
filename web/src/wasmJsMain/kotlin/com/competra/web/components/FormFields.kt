package com.competra.web.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.competra.web.utils.availableTimeZones
import com.competra.web.utils.zoneOffsetLabel

/**
 * Поле даты «только для чтения», открывающее всплывающий календарь (Material3 DatePicker).
 *
 * @param displayValue уже отформатированная строка даты (или пустая).
 * @param initialUtcMillis начальная дата для календаря (UTC-полночь) либо null.
 * @param onPick колбэк с выбранной датой — UTC-полночь выбранного дня (как отдаёт DatePicker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    displayValue: String,
    initialUtcMillis: Long?,
    modifier: Modifier = Modifier,
    onPick: (utcMillis: Long) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("ГГГГ-ММ-ДД") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        // Прозрачный слой поверх поля перехватывает клик и открывает календарь.
        Box(modifier = Modifier.matchParentSize().clickable { showDialog = true })
    }

    if (showDialog) {
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let(onPick)
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * Универсальный выпадающий список «ключ → метка».
 *
 * @param selectedKey текущий выбранный ключ.
 * @param options пары (ключ, отображаемая метка).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LabeledDropdown(
    label: String,
    selectedKey: T,
    options: List<Pair<T, String>>,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedKey }?.second ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(key); expanded = false },
                )
            }
        }
    }
}

/** Поле ввода времени в формате ЧЧ:ММ. */
@Composable
fun TimeField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() || it == ':' }.take(5)
            onChange(filtered)
        },
        label = { Text(label) },
        placeholder = { Text("ЧЧ:ММ") },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/**
 * Поле выбора часового пояса: read-only поле + диалог со списком зон и поиском.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeZoneField(
    zoneId: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val allZones = remember { availableTimeZones() }
    val filtered = remember(query, allZones) {
        if (query.isBlank()) allZones else allZones.filter { it.contains(query, ignoreCase = true) }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = zoneOffsetLabel(zoneId),
            onValueChange = {},
            readOnly = true,
            label = { Text("Часовой пояс *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Box(modifier = Modifier.matchParentSize().clickable { showDialog = true })
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Часовой пояс") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Поиск") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    val listState = rememberLazyListState()
                    LaunchedEffect(filtered) {
                        val idx = filtered.indexOf(zoneId)
                        if (idx >= 0) listState.scrollToItem(idx)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(top = 8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(filtered) { zone ->
                            Text(
                                text = zoneOffsetLabel(zone),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (zone == zoneId) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable {
                                        onSelect(zone)
                                        showDialog = false
                                    }
                                    .padding(12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Закрыть") }
            },
        )
    }
}
