package com.competra.web.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get

@Composable
fun FileUploadButton(
    label: String,
    accept: String = ".xml",
    enabled: Boolean = true,
    onFileSelected: (ByteArray) -> Unit,
) {
    Button(
        enabled = enabled,
        onClick = {
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            input.accept = accept
            input.onchange = {
                val file = input.files?.get(0) ?: return@onchange
                val reader = FileReader()
                reader.onload = { event ->
                    val buffer = (event.target as FileReader).result
                    // ArrayBuffer → ByteArray через JS interop
                    val uint8 = js("new Uint8Array(buffer)")
                    val bytes = ByteArray(uint8.length as Int) { i -> (uint8[i] as Int).toByte() }
                    onFileSelected(bytes)
                }
                reader.readAsArrayBuffer(file)
            }
            input.click()
        }
    ) {
        Text(label)
    }
}
