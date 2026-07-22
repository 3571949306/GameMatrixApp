package com.gamecenter.app.modules.bridge

import android.content.Context
import android.util.Log
import com.gamecenter.app.modules.bridge.generated.ModuleStoreFlutterApi
import com.gamecenter.app.modules.bridge.generated.ModuleStoreHostApi
import com.gamecenter.app.modules.core.ModuleCoreFacade
import com.gamecenter.app.modules.core.ModuleEventBus
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor

object FlutterStoreEngineManager {
    const val ENGINE_ID = "game_matrix_main_engine"
    private const val TAG = "FlutterStoreEngine"
    @Volatile private var initialized = false

    @Synchronized
    fun getOrCreate(context: Context): FlutterEngine {
        FlutterEngineCache.getInstance().get(ENGINE_ID)?.let { return it }
        val appContext = context.applicationContext
        val engine = FlutterEngine(appContext)
        val messenger = engine.dartExecutor.binaryMessenger
        val facade = ModuleCoreFacade.getInstance(appContext)
        val mapper = ModuleBridgeMapper(facade, appContext)
        ModuleStoreHostApi.setUp(messenger, PigeonModuleApiImpl(appContext))
        val flutterApi = ModuleStoreFlutterApi(messenger)
        ModuleEventBus.addObserver { event ->
            flutterApi.onModuleEvent(mapper.event(event)) { result ->
                result.exceptionOrNull()?.let { Log.w(TAG, "Flutter event delivery failed", it) }
            }
        }
        engine.navigationChannel.setInitialRoute("/store")
        engine.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault())
        FlutterEngineCache.getInstance().put(ENGINE_ID, engine)
        initialized = true
        Log.i(TAG, "Cached Flutter engine initialized: $ENGINE_ID")
        return engine
    }

    fun isInitialized(): Boolean = initialized && FlutterEngineCache.getInstance().contains(ENGINE_ID)
}
