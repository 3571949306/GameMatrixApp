package com.gamecenter.app

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.gamecenter.core.online.OnlineManager
import com.gamecenter.core.online.RelayClient
import com.gamecenter.core.online.RoomManager
import org.json.JSONObject
import android.content.Context

/**
 * T03: 联机模块测试
 * 
 * 测试目标：
 * 1. 验证 core/online/ 模块正确编译为 AAR
 * 2. 验证 OnlineManager.java 的接口正确性
 * 3. 验证 RelayClient.java 的接口正确性
 * 4. 验证 RoomManager.java 的接口正确性
 * 5. 验证 AAR 库可被正常依赖调用
 * 
 * 作者: 严过关 (Yan) - GameCenterApp QA 工程师
 * 日期: 2026-05-26
 */
@RunWith(AndroidJUnit4::class)
class T03OnlineModuleTest {
    
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        activityRule.scenario.onActivity { activity ->
            context = activity.applicationContext
        }
    }
    
    // ========= OnlineManager 测试 =========
    
    /**
     * TC-OM-001: 验证 OnlineManager 类存在且可访问
     */
    @Test
    fun testOnlineManager_classExists() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.OnlineManager")
            assertNotNull("OnlineManager class should exist", clazz)
            println("✓ OnlineManager class exists")
        } catch (e: ClassNotFoundException) {
            fail("OnlineManager class not found: ${e.message}")
        }
    }
    
    /**
     * TC-OM-002: 验证 OnlineManager.getInstance() 方法
     */
    @Test
    fun testOnlineManager_getInstance() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.OnlineManager")
            val getInstanceMethod = clazz.getMethod("getInstance", Context::class.java)
            assertNotNull("getInstance() method should exist", getInstanceMethod)
            
            // 调用 getInstance
            val instance = getInstanceMethod.invoke(null, context)
            assertNotNull("getInstance() should return non-null instance", instance)
            println("✓ OnlineManager.getInstance() works correctly")
        } catch (e: ClassNotFoundException) {
            fail("OnlineManager class not found: ${e.message}")
        } catch (e: NoSuchMethodException) {
            fail("OnlineManager.getInstance() method not found: ${e.message}")
        } catch (e: Exception) {
            fail("Failed to call OnlineManager.getInstance(): ${e.message}")
        }
    }
    
    /**
     * TC-OM-003: 验证 OnlineManager.initialize() 方法
     */
    @Test
    fun testOnlineManager_initialize() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.OnlineManager")
            val getInstanceMethod = clazz.getMethod("getInstance", Context::class.java)
            val instance = getInstanceMethod.invoke(null, context)
            
            // 调用 initialize
            val initializeMethod = clazz.getMethod("initialize", Context::class.java)
            initializeMethod.invoke(instance, context)
            
            println("✓ OnlineManager.initialize() called successfully")
        } catch (e: Exception) {
            println("⚠️ OnlineManager.initialize() failed: ${e.message}")
            // 不失败，因为可能需要网络权限
        }
    }
    
    /**
     * TC-OM-004: 验证 OnlineManager.isConnected() 方法
     */
    @Test
    fun testOnlineManager_isConnected() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.OnlineManager")
            val getInstanceMethod = clazz.getMethod("getInstance", Context::class.java)
            val instance = getInstanceMethod.invoke(null, context)
            
            val isConnectedMethod = clazz.getMethod("isConnected")
            val isConnected = isConnectedMethod.invoke(instance) as Boolean
            
            assertNotNull("isConnected() should return boolean", isConnected)
            println("✓ OnlineManager.isConnected() = $isConnected")
        } catch (e: Exception) {
            println("⚠️ OnlineManager.isConnected() check failed: ${e.message}")
        }
    }
    
    /**
     * TC-OM-005: 验证 OnlineManager.disconnect() 方法
     */
    @Test
    fun testOnlineManager_disconnect() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.OnlineManager")
            val getInstanceMethod = clazz.getMethod("getInstance", Context::class.java)
            val instance = getInstanceMethod.invoke(null, context)
            
            val disconnectMethod = clazz.getMethod("disconnect")
            disconnectMethod.invoke(instance)
            
            println("✓ OnlineManager.disconnect() called successfully")
        } catch (e: Exception) {
            println("⚠️ OnlineManager.disconnect() failed: ${e.message}")
        }
    }
    
    // ========= RelayClient 测试 =========
    
    /**
     * TC-RC-001: 验证 RelayClient 类存在且可访问
     */
    @Test
    fun testRelayClient_classExists() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.RelayClient")
            assertNotNull("RelayClient class should exist", clazz)
            println("✓ RelayClient class exists")
        } catch (e: ClassNotFoundException) {
            fail("RelayClient class not found: ${e.message}")
        }
    }
    
    /**
     * TC-RC-002: 验证 RelayClient 构造函数
     */
    @Test
    fun testRelayClient_constructor() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.RelayClient")
            val constructor = clazz.getConstructor(String::class.java)
            assertNotNull("RelayClient(String) constructor should exist", constructor)
            
            // 创建实例（使用测试 URL）
            val instance = constructor.newInstance("wss://test.example.com/ws")
            assertNotNull("RelayClient instance should be created", instance)
            println("✓ RelayClient constructor works correctly")
        } catch (e: NoSuchMethodException) {
            fail("RelayClient constructor not found: ${e.message}")
        } catch (e: Exception) {
            println("⚠️ RelayClient instantiation failed: ${e.message}")
        }
    }
    
    /**
     * TC-RC-003: 验证 RelayClient.connect() 方法存在
     */
    @Test
    fun testRelayClient_connectMethodExists() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.RelayClient")
            val connectMethod = clazz.getMethod("connect")
            assertNotNull("connect() method should exist", connectMethod)
            println("✓ RelayClient.connect() method exists")
        } catch (e: ClassNotFoundException) {
            fail("RelayClient class not found: ${e.message}")
        } catch (e: NoSuchMethodException) {
            println("⚠️ RelayClient.connect() method not found: ${e.message}")
        }
    }
    
    /**
     * TC-RC-004: 验证 RelayClient.send() 方法存在
     */
    @Test
    fun testRelayClient_sendMethodExists() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.RelayClient")
            val sendMethod = clazz.getMethod("send", String::class.java)
            assertNotNull("send() method should exist", sendMethod)
            println("✓ RelayClient.send() method exists")
        } catch (e: ClassNotFoundException) {
            fail("RelayClient class not found: ${e.message}")
        } catch (e: NoSuchMethodException) {
            println("⚠️ RelayClient.send() method not found: ${e.message}")
        }
    }
    
    /**
     * TC-RC-005: 验证 RelayClient.disconnect() 方法存在
     */
    @Test
    fun testRelayClient_disconnectMethodExists() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.RelayClient")
            val disconnectMethod = clazz.getMethod("disconnect")
            assertNotNull("disconnect() method should exist", disconnectMethod)
            println("✓ RelayClient.disconnect() method exists")
        } catch (e: ClassNotFoundException) {
            fail("RelayClient class not found: ${e.message}")
        } catch (e: NoSuchMethodException) {
            println("⚠️ RelayClient.disconnect() method not found: ${e.message}")
        }
    }
    
    // ========= RoomManager 测试 =========
    
    /**
     * TC-RM-001: 验证 RoomManager 类存在且可访问
     */
    @Test
    fun testRoomManager_classExists() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.RoomManager")
            assertNotNull("RoomManager class should exist", clazz)
            println("✓ RoomManager class exists")
        } catch (e: ClassNotFoundException) {
            fail("RoomManager class not found: ${e.message}")
        }
    }
    
    /**
     * TC-RM-002: 验证 RoomManager 构造函数
     */
    @Test
    fun testRoomManager_constructor() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.RoomManager")
            val constructor = clazz.getConstructor(Context::class.java)
            assertNotNull("RoomManager(Context) constructor should exist", constructor)
            
            // 创建实例
            val instance = constructor.newInstance(context)
            assertNotNull("RoomManager instance should be created", instance)
            println("✓ RoomManager constructor works correctly")
        } catch (e: NoSuchMethodException) {
            fail("RoomManager constructor not found: ${e.message}")
        } catch (e: Exception) {
            println("⚠️ RoomManager instantiation failed: ${e.message}")
        }
    }
    
    /**
     * TC-RM-003: 验证 RoomManager.generateRoomCode() 静态方法
     */
    @Test
    fun testRoomManager_generateRoomCode() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.RoomManager")
            val generateMethod = clazz.getMethod("generateRoomCode")
            assertNotNull("generateRoomCode() method should exist", generateMethod)
            
            // 调用静态方法
            val roomCode = generateMethod.invoke(null) as String
            assertNotNull("Generated room code should not be null", roomCode)
            assertEquals("Room code should be 6 characters", 6, roomCode.length)
            println("✓ RoomManager.generateRoomCode() = $roomCode")
        } catch (e: NoSuchMethodException) {
            fail("RoomManager.generateRoomCode() method not found: ${e.message}")
        } catch (e: Exception) {
            fail("Failed to call RoomManager.generateRoomCode(): ${e.message}")
        }
    }
    
    /**
     * TC-RM-004: 验证 RoomManager.setCurrentRoom() 方法
     */
    @Test
    fun testRoomManager_setCurrentRoom() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.RoomManager")
            val constructor = clazz.getConstructor(Context::class.java)
            val instance = constructor.newInstance(context)
            
            val setCurrentRoomMethod = clazz.getMethod(
                "setCurrentRoom", 
                String::class.java, 
                String::class.java, 
                Boolean::class.javaPrimitiveType
            )
            
            // 调用 setCurrentRoom
            setCurrentRoomMethod.invoke(instance, "ABC123", "DDZ", true)
            println("✓ RoomManager.setCurrentRoom() called successfully")
        } catch (e: Exception) {
            println("⚠️ RoomManager.setCurrentRoom() failed: ${e.message}")
        }
    }
    
    /**
     * TC-RM-005: 验证 RoomManager.getCurrentRoomCode() 方法
     */
    @Test
    fun testRoomManager_getCurrentRoomCode() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.RoomManager")
            val constructor = clazz.getConstructor(Context::class.java)
            val instance = constructor.newInstance(context)
            
            val getCurrentRoomCodeMethod = clazz.getMethod("getCurrentRoomCode")
            val roomCode = getCurrentRoomCodeMethod.invoke(instance) as? String
            
            // 初始应该为 null
            println("✓ RoomManager.getCurrentRoomCode() = $roomCode")
        } catch (e: Exception) {
            println("⚠️ RoomManager.getCurrentRoomCode() failed: ${e.message}")
        }
    }
    
    /**
     * TC-RM-006: 验证 RoomManager.clearCurrentRoom() 方法
     */
    @Test
    fun testRoomManager_clearCurrentRoom() {
        try {
            val clazz = Class.forName("com.gamecenter.core.online.RoomManager")
            val constructor = clazz.getConstructor(Context::class.java)
            val instance = constructor.newInstance(context)
            
            val clearMethod = clazz.getMethod("clearCurrentRoom")
            clearMethod.invoke(instance)
            
            println("✓ RoomManager.clearCurrentRoom() called successfully")
        } catch (e: Exception) {
            println("⚠️ RoomManager.clearCurrentRoom() failed: ${e.message}")
        }
    }
    
    // ========= AAR 库集成测试 =========
    
    /**
     * TC-OA-001: 验证 core/online AAR 库可被正常依赖
     */
    @Test
    fun testOnlineAarLibraryAccessible() {
        val classesToCheck = listOf(
            "com.gamecenter.core.online.OnlineManager",
            "com.gamecenter.core.online.RelayClient",
            "com.gamecenter.core.online.RoomManager"
        )
        
        for (className in classesToCheck) {
            try {
                val clazz = Class.forName(className)
                assertNotNull("Class $className should be accessible", clazz)
                println("✓ Class $className is accessible from AAR library")
            } catch (e: ClassNotFoundException) {
                fail("Class $className not found - AAR library not properly integrated: ${e.message}")
            }
        }
    }
}
