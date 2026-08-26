package com.competra.web.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.web.utils.DebugErrorReporter

/**
 * Плашка над таб-баром с последними сетевыми/прочими ошибками — видна только в debug-режиме
 * (см. [com.competra.web.utils.isDebugEnvironment], откуда [DebugErrorReporter] сам решает,
 * копить ли ошибки). Подключается в `bottomBar` каждого экрана, см. правило в CLAUDE.md.
 */
@Composable
fun DebugErrorBanner(modifier: Modifier = Modifier) {
    val entries = DebugErrorReporter.entries
    if (entries.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer)) {
        entries.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "DEBUG: ${entry.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { DebugErrorReporter.dismiss(entry.id) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Скрыть",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}
