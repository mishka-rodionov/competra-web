package com.competra.web.di

import com.competra.data.api.createHttpClient
import com.competra.data.auth.AuthRepository
import com.competra.data.auth.LocalStorageTokenStorage
import com.competra.data.auth.TokenStorage
import com.competra.data.repository.CompetitionRepository
import com.competra.data.repository.DistanceRepository
import org.koin.dsl.module

val appModule = module {
    single<TokenStorage> { LocalStorageTokenStorage() }
    single { createHttpClient(get()) }
    single { AuthRepository(get(), get()) }
    single { CompetitionRepository(get()) }
    single { DistanceRepository(get()) }
}
