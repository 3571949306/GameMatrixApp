package com.gamecenter.app.modules.bridge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import io.flutter.embedding.android.FlutterActivity

class FlutterModuleStoreActivity : FlutterActivity() {
    override fun getCachedEngineId(): String = FlutterStoreEngineManager.ENGINE_ID

    override fun shouldDestroyEngineWithHost(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        FlutterStoreEngineManager.getOrCreate(applicationContext)
        super.onCreate(savedInstanceState)
    }

    companion object {
        private const val TAG = "FlutterModuleStore"

        fun launch(context: Context): Boolean = runCatching {
            FlutterStoreEngineManager.getOrCreate(context)
            context.startActivity(Intent(context, FlutterModuleStoreActivity::class.java))
            true
        }.getOrElse { error ->
            Log.e(TAG, "Flutter store initialization failed; using legacy store", error)
            false
        }
    }
}
