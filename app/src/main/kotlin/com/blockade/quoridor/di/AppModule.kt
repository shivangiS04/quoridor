package com.blockade.quoridor.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule
// SupabaseService, UserPreferences, and GameRepository are all @Singleton @Inject constructor — no manual bindings needed.
