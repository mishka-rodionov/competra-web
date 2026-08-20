package com.competra.web.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Открывает нативный диалог выбора файла в браузере и возвращает имя и текстовое
 * содержимое выбранного файла. IOF XML — текст UTF-8, поэтому читаем как text.
 */
@JsFun(
    "(accept, onPicked) => { " +
        "const input = document.createElement('input'); " +
        "input.type = 'file'; " +
        "input.accept = accept; " +
        "input.onchange = async (e) => { " +
        "const file = e.target.files && e.target.files[0]; " +
        "if (file) { const text = await file.text(); onPicked(file.name, text); } " +
        "}; " +
        "input.click(); }"
)
private external fun jsPickFile(accept: String, onPicked: (String, String) -> Unit)

/** Выбор IOF XML файла. Колбэк получает имя файла и его содержимое. */
fun pickXmlFile(onPicked: (fileName: String, content: String) -> Unit) =
    jsPickFile(".xml", onPicked)

/** Выбор HTML файла (протокол результатов). Колбэк получает имя файла и его содержимое. */
fun pickHtmlFile(onPicked: (fileName: String, content: String) -> Unit) =
    jsPickFile(".html", onPicked)

/** Выбор GPX файла (трек тренировки). Колбэк получает имя файла и его содержимое. */
fun pickGpxFile(onPicked: (fileName: String, content: String) -> Unit) =
    jsPickFile(".gpx", onPicked)

/**
 * Открывает нативный диалог выбора файла и возвращает его как base64 через FileReader
 * (в отличие от [jsPickFile], который читает файл как текст — годится для PDF/картинок).
 */
@JsFun(
    "(accept, onPicked) => { " +
        "const input = document.createElement('input'); " +
        "input.type = 'file'; " +
        "input.accept = accept; " +
        "input.onchange = (e) => { " +
        "const file = e.target.files && e.target.files[0]; " +
        "if (!file) return; " +
        "const reader = new FileReader(); " +
        "reader.onload = () => { " +
        "const dataUrl = reader.result; " +
        "const base64 = dataUrl.substring(dataUrl.indexOf(',') + 1); " +
        "onPicked(file.name, file.type, base64); " +
        "}; " +
        "reader.readAsDataURL(file); " +
        "}; " +
        "input.click(); }"
)
private external fun jsPickBinaryFile(accept: String, onPicked: (String, String, String) -> Unit)

/** Выбор бинарного файла (например, карты дистанции — PNG/JPG/PDF). Колбэк получает имя, MIME-тип и байты. */
@OptIn(ExperimentalEncodingApi::class)
fun pickBinaryFile(accept: String, onPicked: (fileName: String, contentType: String, bytes: ByteArray) -> Unit) {
    jsPickBinaryFile(accept) { name, mime, base64 ->
        onPicked(name, mime.ifBlank { "application/octet-stream" }, Base64.decode(base64))
    }
}

/**
 * Выбор карты дистанции — растр, экспортированный из mapper (Export map ... с включённым
 * "Copy WGS84 map corners for Competra"). Только растровые форматы: PDF не декодируется
 * Skia-рендерером [com.competra.web.components.DistanceMapView] и не наложится на OSM.
 */
fun pickDistanceMapFile(onPicked: (fileName: String, contentType: String, bytes: ByteArray) -> Unit) =
    pickBinaryFile(".png,.jpg,.jpeg", onPicked)
