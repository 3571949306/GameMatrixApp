package com.gamecenter.app.moduleloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.gamecenter.app.interfaces.IModule;
import com.gamecenter.app.interfaces.IModuleLoader;
import com.gamecenter.app.models.ModuleInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class ModuleLoaderV2Test {

    private ModuleLoaderV2 moduleLoader;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        moduleLoader = ModuleLoaderV2.getInstance(context);
    }

    @Test
    public void testGetInstance_IsSingleton() {
        ModuleLoaderV2 instance2 = ModuleLoaderV2.getInstance(context);
        assertEquals(moduleLoader, instance2);
    }

    @Test
    public void testLoadModule_NullModuleInfo() {
        IModuleLoader.ModuleLoadException exception = assertThrows(
            IModuleLoader.ModuleLoadException.class, 
            () -> moduleLoader.loadModule((ModuleInfo) null)
        );
        assertEquals(IModuleLoader.ModuleLoadException.ERROR_MODULE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    public void testLoadModule_ApkNotFound() {
        ModuleInfo mockInfo = new ModuleInfo();
        mockInfo.setModuleId("non_existent_module");
        mockInfo.setVersionCode(1);
        mockInfo.setVersionName("1.0.0");

        IModuleLoader.ModuleLoadException exception = assertThrows(
            IModuleLoader.ModuleLoadException.class,
            () -> moduleLoader.loadModule(mockInfo)
        );
        assertEquals(IModuleLoader.ModuleLoadException.ERROR_MODULE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    public void testUnloadModule_NonExistent() throws IModuleLoader.ModuleUnloadException {
        // 应该静默处理，不抛异常
        moduleLoader.unloadModule("non_existent_module");
        assertFalse(moduleLoader.isModuleLoaded("non_existent_module"));
    }

    @Test
    public void testReloadModule_ThrowsExceptionWithoutInfo() {
        IModuleLoader.ModuleLoadException exception = assertThrows(
            IModuleLoader.ModuleLoadException.class,
            () -> moduleLoader.reloadModule("any_module")
        );
        assertEquals(IModuleLoader.ModuleLoadException.ERROR_MODULE_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("需要 ModuleInfo"));
    }

    @Test
    public void testGetLoadedModules() {
        List<String> modules = moduleLoader.getLoadedModules();
        assertNotNull(modules);
        // Initially empty
        assertEquals(0, modules.size());
    }

    @Test
    public void testGetStatus() {
        String status = moduleLoader.getStatus();
        assertNotNull(status);
        assertTrue(status.contains("ModuleLoaderV2"));
    }

    @Test
    public void testSetFrameworkVersionCode() {
        moduleLoader.setFrameworkVersionCode(5);
        assertEquals(5, moduleLoader.getFrameworkVersionCode());
        
        moduleLoader.setFrameworkVersionCode(-1);
        assertEquals(1, moduleLoader.getFrameworkVersionCode()); // Minimum is 1
    }
}
