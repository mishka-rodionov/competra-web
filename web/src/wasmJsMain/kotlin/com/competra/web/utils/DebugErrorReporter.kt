package com.competra.web.utils

import androidx.compose.runtime.mutableStateListOf

data class DebugErrorEntry(val id: Long, val message: String)

private const val MAX_ENTRIES = 5

/**
 * Копит сетевые (и прочие) ошибки для показа плашкой над таб-баром в debug-режиме
 * (см. [isDebugEnvironment]) — вместо того, чтобы они молча оседали в консоли и терялись,
 * как это было с зависанием карты дистанции (см. [com.competra.web.utils.loadImageBitmapFromUrl]).
 *
 * Конвенция проекта: любой новый сетевой вызов должен в catch-блоке звать [report] с коротким
 * описанием (что за запрос и что пошло не так), прежде чем проглатывать ошибку молча. См. CLAUDE.md.
 */
object DebugErrorReporter {
    private var nextId = 0L
    private val _entries = mutableStateListOf<DebugErrorEntry>()
    val entries: List<DebugErrorEntry> get() = _entries

    fun report(message: String) {
        if (!isDebugEnvironment()) return
        _entries.add(DebugErrorEntry(nextId++, message))
        while (_entries.size > MAX_ENTRIES) _entries.removeAt(0)
    }

    fun dismiss(id: Long) {
        _entries.removeAll { it.id == id }
    }
}
