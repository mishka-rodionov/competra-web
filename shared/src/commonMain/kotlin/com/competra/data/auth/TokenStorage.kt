package com.competra.data.auth

interface TokenStorage {
    fun getToken(): String?
    fun getRefreshToken(): String?
    fun saveTokens(accessToken: String, refreshToken: String)
    fun clearToken()
    fun isLoggedIn(): Boolean = getToken() != null
}
