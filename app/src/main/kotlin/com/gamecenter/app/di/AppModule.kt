package com.gamecenter.app.di

import android.content.Context
import com.gamecenter.app.SettingsManager
import com.gamecenter.app.network.OkHttpClientProvider
import com.gamecenter.app.update.UpdateManager
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
    fun provideSettingsManager(
        @ApplicationContext context: Context
    ): SettingsManager = SettingsManager.getInstance(context)
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context
    ): OkHttpClient = OkHttpClientProvider.getInstance(context).httpClient
    
    @Provides
    @Singleton
    fun provideUpdateManager(): UpdateManager = UpdateManager.getInstance()
}
