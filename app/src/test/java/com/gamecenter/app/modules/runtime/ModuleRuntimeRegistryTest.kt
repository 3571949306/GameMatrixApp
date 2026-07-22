package com.gamecenter.app.modules.runtime

import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.DeliveryType
import com.gamecenter.app.modules.catalog.RuntimeType
import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleRuntimeRegistryTest {

    @Test
    fun `registry contains every catalog runtime`() {
        val registry = ModuleRuntimeRegistry()

        RuntimeType.entries.forEach { runtime ->
            val module = CatalogModule(
                id = "test_${runtime.wireValue}",
                name = runtime.wireValue,
                runtimeType = runtime,
                deliveryType = DeliveryType.BUILTIN,
                route = "/store",
                entry = "index.html",
                serviceType = "test",
                launcherId = "test"
            )
            assertEquals(runtime, registry.forModule(module).runtimeType)
        }
    }
}
