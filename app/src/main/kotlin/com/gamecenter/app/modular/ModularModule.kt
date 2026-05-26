package com.gamecenter.app.modular

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModularModule {

    @Provides
    @Singleton
    fun provideModuleDatabase(
        @ApplicationContext context: Context
    ): ModuleDatabase = ModuleDatabase.getDatabase(context)

    @Provides
    @Singleton
    fun provideModuleDao(database: ModuleDatabase): ModuleDao = database.moduleDao()

    @Provides
    @Singleton
    fun provideModuleCacheDir(
        @ApplicationContext context: Context
    ): File = File(context.filesDir, "dynamic_modules").also { it.mkdirs() }

    @Provides
    @Singleton
    fun provideModuleDownloader(
        okHttpClient: OkHttpClient,
        moduleCacheDir: File
    ): ModuleDownloader = ModuleDownloader(okHttpClient, moduleCacheDir)

    @Provides
    @Singleton
    fun provideModuleCacheManager(
        @ApplicationContext context: Context,
        moduleDao: ModuleDao,
        downloader: ModuleDownloader
    ): ModuleCacheManager = ModuleCacheManager(context, moduleDao, downloader)

    @Provides
    @Singleton
    fun provideModuleResourceLoader(
        @ApplicationContext context: Context
    ): ModuleResourceLoader = ModuleResourceLoader(context)

    @Provides
    @Singleton
    fun provideModuleLoader(
        @ApplicationContext context: Context,
        resourceLoader: ModuleResourceLoader
    ): ModuleLoader = ModuleLoader(context, resourceLoader)

    @Provides
    @Singleton
    fun provideModuleManager(
        moduleDao: ModuleDao,
        downloader: ModuleDownloader,
        cacheManager: ModuleCacheManager,
        moduleLoader: ModuleLoader
    ): ModuleManager = ModuleManager(moduleDao, downloader, cacheManager, moduleLoader)
}
