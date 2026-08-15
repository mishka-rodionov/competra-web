package com.competra.data.auth

@JsFun("(key) => localStorage.getItem(key) ?? null")
private external fun jsGet(key: String): String?

@JsFun("(key, value) => { localStorage.setItem(key, value) }")
private external fun jsSet(key: String, value: String)

@JsFun("(key) => { localStorage.removeItem(key) }")
private external fun jsRemove(key: String)

class LocalStorageTokenStorage : TokenStorage {
    private val ACCESS_KEY = "competra_access_token"
    private val REFRESH_KEY = "competra_refresh_token"
    override fun getToken(): String? = jsGet(ACCESS_KEY)
    override fun getRefreshToken(): String? = jsGet(REFRESH_KEY)
    override fun saveTokens(accessToken: String, refreshToken: String) {
        jsSet(ACCESS_KEY, accessToken)
        jsSet(REFRESH_KEY, refreshToken)
    }
    override fun clearToken() {
        jsRemove(ACCESS_KEY)
        jsRemove(REFRESH_KEY)
    }
}
