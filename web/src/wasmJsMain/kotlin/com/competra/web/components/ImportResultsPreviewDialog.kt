package com.competra.web.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.web.utils.ImportResultRow
import com.competra.web.utils.ParsedResultRow

/**
 * Превью-дифф перед импортом результатов из HTML: изменившиеся заматченные строки
 * (с чекбоксом на каждой — можно исключить отдельные строки из импорта) и отдельно,
 * только информационно, строки с нераспознанным стартовым номером.
 */
@Composable
fun ImportResultsPreviewDialog(
    competitionTitle: String,
    changed: List<ImportResultRow>,
    unmatched: List<ParsedResultRow>,
    onDismiss: () -> Unit,
    onConfirm: (List<ImportResultRow>) -> Unit,
) {
    val checkedById = remember(changed) {
        mutableStateMapOf<String, Boolean>().apply { changed.forEach { this[it.request.id] = true } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт результатов из HTML") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Импорт применится к текущему соревнованию: $competitionTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                when {
                    changed.isEmpty() && unmatched.isEmpty() -> Text(
                        "В файле не найдено ни одной строки результатов.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    changed.isEmpty() -> Text(
                        "Совпадений с текущими участниками не найдено — проверьте, что выбран файл именно этого соревнования.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Text("Изменения (${changed.size}):", style = MaterialTheme.typography.titleSmall)
                }

                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(changed, key = { it.request.id }) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checkedById[row.request.id] == true,
                                onCheckedChange = { checkedById[row.request.id] = it },
                            )
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "№${row.participant.startNumber} ${row.participant.lastName} ${row.participant.firstName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    row.changeSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (unmatched.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Не распознано (${unmatched.size}) — стартовый номер не найден среди участников, будут пропущены:",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        items(unmatched) { row ->
                            Text(
                                "№${row.startNumber} ${row.fullName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val selectedCount = changed.count { checkedById[it.request.id] == true }
            Button(
                enabled = selectedCount > 0,
                onClick = { onConfirm(changed.filter { checkedById[it.request.id] == true }) },
            ) {
                Text("Применить ($selectedCount)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
