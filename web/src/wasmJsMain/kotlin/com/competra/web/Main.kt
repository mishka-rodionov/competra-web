package com.competra.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.competra.web.di.appModule
import kotlinx.browser.document
import org.koin.core.context.startKoin

@JsFun("() => location.hash")
private external fun jsLocationHash(): String

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin { modules(appModule) }
    // Deep link для форм Google Play / RuStore: https://competra.ru/#privacy
    val initialPage = when (jsLocationHash()) {
        "#privacy" -> Page.PrivacyPolicy
        else -> Page.Competitions
    }
    ComposeViewport(document.body!!) {
        App(initialPage = initialPage)
    }
}
