package com.competra.data.auth

@JsFun("(key) => localStorage.getItem(key) ?? null")
private external fun jsGet(key: String): String?

@JsFun("(key, value) => { localStorage.setItem(key, value) }")
private external fun jsSet(key: String, value: String)

@JsFun("(key) => { localStorage.removeItem(key) }")
private external fun jsRemove(key: String)

class LocalStorageTokenStorage : TokenStorage {
    private val KEY = "competra_access_token"
    override fun getToken(): String? = jsGet(KEY)
    override fun saveToken(token: String) = jsSet(KEY, token)
    override fun clearToken() = jsRemove(KEY)
}
