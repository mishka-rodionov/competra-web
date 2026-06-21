package com.competra.web.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.web.utils.pickXmlFile

/**
 * Компонент импорта IOF XML файла.
 * Пользователь выбирает уже существующий XML-файл через нативный диалог браузера,
 * содержимое читается и передаётся наверх как ByteArray для отправки на сервер.
 */
@Composable
fun XmlImportField(
    modifier: Modifier = Modifier,
    onXmlReady: (ByteArray) -> Unit,
) {
    var fileName by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            pickXmlFile { name, content ->
                fileName = name
                onXmlReady(content.encodeToByteArray())
            }
        }) {
            Text("Выбрать IOF XML файл")
        }
        fileName?.let {
            Spacer(Modifier.height(4.dp))
            Text("Загружен файл: $it", style = MaterialTheme.typography.bodySmall)
        }
    }
}
