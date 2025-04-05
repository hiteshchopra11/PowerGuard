package com.hackathon.powergaurd.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt module for providing database-related dependencies. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // Database components have been replaced by in-memory implementations
} 