package com.gamecenter.app.modules

import android.content.Context
import android.util.Log
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.core.security.SecureOkHttpFactory
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * 模块版本检查器（T03 新增）。
 * 
 * 负责检查模块更新、比较版本号、判断是否需要加载外部模块。
 * 
 * @author 高见远 (Gao)
 * @version 1.0
 * @since 2026-05-25
 */
object ModuleVersionChecker {
    
    private const val TAG = "ModuleVersionChecker"
    private val MODULES_URL: String get() = BuildConfig.MODULES_URL
    private const val TIMEOUT_MS = 15000
    
    /**
     * 比较内置版本和外部版本。
     * 
     * @param builtInVersion 内置版本号
     * @param storeVersion 商店版本号
     * @return 比较结果：-1=商店版本更低, 0=版本相同, 1=商店版本更高
     */
    fun compareVersions(builtInVersion: Int, storeVersion: Int): Int {
        return when {
            storeVersion > builtInVersion -> 1
            storeVersion == builtInVersion -> 0
            else -> -1
        }
    }
    
    /**
     * 检查模块是否有更新。
     * 
     * @param context Context 对象
     * @param moduleId 模块 ID
     * @param callback 结果回调接口
     */
    fun checkForUpdates(
        context: Context,
        moduleId: String,
        callback: (ModuleManifest?, error: String?) -> Unit
    ) {
        Thread {
            try {
                val client = SecureOkHttpFactory.buildModuleClient()
                val request = Request.Builder()
                    .url(MODULES_URL)
                    .build()
                
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    callback(null, "HTTP ${response.code}")
                    response.close()
                    return@Thread
                }
                
                val body = response.body?.string()
                response.close()
                
                if (body.isNullOrEmpty()) {
                    callback(null, "响应体为空")
                    return@Thread
                }
                
                // 解析模块列表
                val json = JSONObject(body)
                val modulesArray = json.getJSONArray("modules")
                
                for (i in 0 until modulesArray.length()) {
                    val moduleJson = modulesArray.getJSONObject(i)
                    val id = moduleJson.getString("id")
                    
                    if (id == moduleId) {
                        val manifest = ModuleManifest.fromJson(moduleJson)
                        callback(manifest, null)
                        return@Thread
                    }
                }
                
                callback(null, "模块未找到: $moduleId")
            } catch (e: IOException) {
                Log.e(TAG, "网络请求失败: ${e.message}")
                callback(null, "网络请求失败: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "解析失败: ${e.message}")
                callback(null, "解析失败: ${e.message}")
            }
        }.start()
    }
    
    /**
     * 判断是否应该加载外部模块。
     * 
     * @param builtInVersion 内置版本号
     * @param externalModule 外部模块清单
     * @return 是否应该加载外部模块
     */
    fun shouldLoadExternal(builtInVersion: Int, externalModule: ModuleManifest): Boolean {
        return externalModule.versionCode > builtInVersion
    }
    
    /**
     * 获取内置模块版本号。
     * 
     * @param context Context 对象
     * @param moduleId 模块 ID
     * @return 内置版本号，如果未找到则返回 0
     */
    fun getBuiltInVersion(context: Context, moduleId: String): Int {
        return try {
            val inputStream = context.assets.open("modules.json")
            val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
            val json = JSONObject(reader.readText())
            reader.close()
            
            val modulesArray = json.getJSONArray("modules")
            for (i in 0 until modulesArray.length()) {
                val moduleJson = modulesArray.getJSONObject(i)
                if (moduleJson.getString("id") == moduleId) {
                    return moduleJson.getInt("versionCode")
                }
            }
            0
        } catch (e: Exception) {
            Log.w(TAG, "读取内置模块版本失败: ${e.message}")
            0
        }
    }
    
    /**
     * 检查所有内置模块是否有更新。
     * 
     * @param context Context 对象
     * @param callback 结果回调接口（模块 ID 和远程清单的配对列表，错误信息）
     */
    fun checkAllBuiltInModulesForUpdates(
        context: Context,
        callback: (List<Pair<String, ModuleManifest>>, error: String?) -> Unit
    ) {
        Thread {
            val updates = mutableListOf<Pair<String, ModuleManifest>>()
            var error: String? = null
            
            try {
                val client = SecureOkHttpFactory.buildModuleClient()
                val request = Request.Builder()
                    .url(MODULES_URL)
                    .build()
                
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    callback(emptyList(), "HTTP ${response.code}")
                    response.close()
                    return@Thread
                }
                
                val body = response.body?.string()
                response.close()
                
                if (body.isNullOrEmpty()) {
                    callback(emptyList(), "响应体为空")
                    return@Thread
                }
                
                // 解析远程模块列表
                val remoteJson = JSONObject(body)
                val remoteModulesArray = remoteJson.getJSONArray("modules")
                val remoteModules = mutableMapOf<String, ModuleManifest>()
                
                for (i in 0 until remoteModulesArray.length()) {
                    val moduleJson = remoteModulesArray.getJSONObject(i)
                    val manifest = ModuleManifest.fromJson(moduleJson)
                    remoteModules[manifest.id] = manifest
                }
                
                // 读取内置模块列表
                val inputStream = context.assets.open("modules.json")
                val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
                val localJson = JSONObject(reader.readText())
                reader.close()
                
                val localModulesArray = localJson.getJSONArray("modules")
                
                // 比较版本号
                for (i in 0 until localModulesArray.length()) {
                    val moduleJson = localModulesArray.getJSONObject(i)
                    val id = moduleJson.getString("id")
                    val builtInVersion = moduleJson.getInt("versionCode")
                    
                    val remoteModule = remoteModules[id]
                    if (remoteModule != null && remoteModule.versionCode > builtInVersion) {
                        updates.add(Pair(id, remoteModule))
                    }
                }
                
                callback(updates, null)
            } catch (e: IOException) {
                Log.e(TAG, "网络请求失败: ${e.message}")
                callback(emptyList(), "网络请求失败: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "解析失败: ${e.message}")
                callback(emptyList(), "解析失败: ${e.message}")
            }
        }.start()
    }
}
