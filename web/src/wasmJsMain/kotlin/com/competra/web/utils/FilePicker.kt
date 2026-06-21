package com.competra.web.utils

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
