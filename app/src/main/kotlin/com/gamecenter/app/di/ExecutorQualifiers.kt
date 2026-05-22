package com.gamecenter.app.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoExecutor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiExecutor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NetworkExecutor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GameExecutor
