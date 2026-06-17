package com.competra.web.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Компонент импорта XML-контента.
 * Пользователь вставляет содержимое IOF XML-файла в текстовое поле.
 * TODO: заменить на нативный file picker через JS interop в следующей итерации.
 */
@Composable
fun XmlImportField(
    modifier: Modifier = Modifier,
    onXmlReady: (ByteArray) -> Unit,
) {
    var xmlText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Button(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Скрыть поле импорта" else "Импорт из Mapper (вставить XML)")
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = xmlText,
                onValueChange = { xmlText = it },
                label = { Text("Вставьте содержимое IOF XML файла") },
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = xmlText.isNotBlank(),
                onClick = {
                    onXmlReady(xmlText.encodeToByteArray())
                    xmlText = ""
                    expanded = false
                },
            ) { Text("Загрузить") }
        }
    }
}
