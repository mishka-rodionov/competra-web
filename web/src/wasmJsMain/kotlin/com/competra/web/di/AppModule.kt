package com.competra.web.di

import com.competra.data.api.createHttpClient
import com.competra.data.api.createPublicHttpClient
import com.competra.data.auth.AuthRepository
import com.competra.data.auth.LocalStorageTokenStorage
import com.competra.data.auth.TokenStorage
import com.competra.data.repository.CompetitionRepository
import com.competra.data.repository.DistanceRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {
    single<TokenStorage> { LocalStorageTokenStorage() }
    single(named("public")) { createPublicHttpClient() }
    single(named("auth")) { createHttpClient(get()) }
    single { AuthRepository(get(named("auth")), get()) }
    single { CompetitionRepository(publicClient = get(named("public")), authClient = get(named("auth"))) }
    single { DistanceRepository(get(named("auth"))) }
}
