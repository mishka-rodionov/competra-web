package com.competra.web.utils

@JsFun(
    "() => { try { " +
        "const h = location.hostname; if (h === 'localhost' || h === '127.0.0.1') return true; " +
        "const u = new URLSearchParams(location.search); " +
        "if (u.get('debug') === '1') { try { localStorage.setItem('debugTools', '1'); } catch (e) {} return true; } " +
        "return localStorage.getItem('debugTools') === '1'; " +
        "} catch (e) { return false; } }"
)
private external fun jsIsDebugEnvironment(): Boolean

/**
 * Признак debug-окружения для web — гейт для инструментов разработчика
 * (преднабор тестовых данных, флаг «тестовое соревнование» на форме создания).
 *
 * Включается, если:
 * - хост `localhost` / `127.0.0.1` (локальная разработка), либо
 * - в URL есть `?debug=1` (флаг при этом сохраняется в `localStorage`, чтобы пережить навигацию), либо
 * - в `localStorage` уже стоит `debugTools=1`.
 *
 * Снять флаг на деплое: `localStorage.removeItem('debugTools')` в консоли браузера.
 */
fun isDebugEnvironment(): Boolean = jsIsDebugEnvironment()
