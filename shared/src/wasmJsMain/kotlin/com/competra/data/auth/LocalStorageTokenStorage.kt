package com.competra.data.auth

import kotlinx.browser.localStorage

class LocalStorageTokenStorage : TokenStorage {
    private val KEY = "competra_access_token"
    override fun getToken(): String? = localStorage.getItem(KEY)
    override fun saveToken(token: String) { localStorage.setItem(KEY, token) }
    override fun clearToken() { localStorage.removeItem(KEY) }
}
