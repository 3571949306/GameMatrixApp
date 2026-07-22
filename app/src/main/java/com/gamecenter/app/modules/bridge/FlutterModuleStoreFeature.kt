package com.gamecenter.app.modules.bridge

import android.content.Context
import androidx.fragment.app.Fragment
import com.gamecenter.app.core.common.FeatureModule
import io.flutter.embedding.android.FlutterFragment

class FlutterModuleStoreFeature : FeatureModule {
    override fun createFragment(context: Context): Fragment {
        FlutterStoreEngineManager.getOrCreate(context)
        return FlutterFragment.withCachedEngine(FlutterStoreEngineManager.ENGINE_ID)
            .shouldAttachEngineToActivity(true)
            .destroyEngineWithFragment(false)
            .build()
    }

    override fun getModuleType(): String = "flutter"
}
