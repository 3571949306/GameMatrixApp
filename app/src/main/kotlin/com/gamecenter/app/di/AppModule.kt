package com.gamecenter.app.di

import android.content.Context
import com.gamecenter.app.database.AppDatabase
import com.gamecenter.app.database.dao.GameStatsDao
import com.gamecenter.app.network.OkHttpClientProvider
import com.gamecenter.app.utils.ErrorReporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideExecutorService(): ExecutorService =
        Executors.newCachedThreadPool()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        okHttpClientProvider: OkHttpClientProvider
    ): OkHttpClient = okHttpClientProvider.httpClient

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides
    @Singleton
    fun provideGameStatsDao(database: AppDatabase): GameStatsDao =
        database.gameStatsDao()

    @Provides
    @Singleton
    fun provideErrorReporter(
        @ApplicationContext context: Context
    ): ErrorReporter = ErrorReporter.getInstance(context)
}
