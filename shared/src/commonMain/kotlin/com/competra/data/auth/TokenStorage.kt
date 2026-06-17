package com.competra.data.auth

interface TokenStorage {
    fun getToken(): String?
    fun saveToken(token: String)
    fun clearToken()
    fun isLoggedIn(): Boolean = getToken() != null
}
