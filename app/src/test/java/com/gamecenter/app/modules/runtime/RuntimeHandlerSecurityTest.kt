package com.gamecenter.app.modules.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.DeliveryType
import com.gamecenter.app.modules.catalog.RuntimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RuntimeHandlerSecurityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `flutter accepts compiled route and rejects unknown route`() {
        val handler = FlutterRuntimeHandler()
        val compiled = module(RuntimeType.FLUTTER, DeliveryType.BUILTIN).copy(route = "/store/installed")
        val missing = compiled.copy(route = "/not-compiled")

        assertTrue(handler.install(context, compiled).success)
        assertEquals("route_missing", handler.install(context, missing).code)
    }

    @Test
    fun `native service rejects unregistered host controller`() {
        val module = module(RuntimeType.NATIVE_SERVICE, DeliveryType.BUILTIN)
            .copy(serviceType = "unregistered_service")

        val result = NativeServiceRuntimeHandler().open(context, module)

        assertFalse(result.success)
        assertEquals("service_type_unsupported", result.code)
    }

    @Test
    fun `asset and unity refuse open before verified content is installed`() {
        val asset = module(RuntimeType.ASSET, DeliveryType.ZIP)
        val unity = module(RuntimeType.UNITY, DeliveryType.CONTENT).copy(launcherId = "missing-launcher")

        assertEquals("asset_missing", AssetRuntimeHandler().open(context, asset).code)
        assertEquals("content_not_installed", UnityRuntimeHandler().open(context, unity).code)
    }

    private fun module(runtime: RuntimeType, delivery: DeliveryType) = CatalogModule(
        id = "security_${runtime.wireValue}",
        name = runtime.wireValue,
        runtimeType = runtime,
        deliveryType = delivery
    )
}
