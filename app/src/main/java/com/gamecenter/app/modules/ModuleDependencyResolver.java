package com.gamecenter.app.modules;

import android.util.Log;
import com.gamecenter.app.modules.ModuleManifest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模块依赖解析器（TD-05）。
 * 
 * 功能：
 * 1. 解析 ModuleManifest.depends（List<String>）
 * 2. 检测循环依赖（使用 Set<String> 记录已访问模块）
 * 3. 获取最高版本依赖
 * 
 * @author 寇豆码 (Kou)
 * @version 1.0
 * @since 2026-05-27
 */
public class ModuleDependencyResolver {
    
    private static final String TAG = "ModuleDependencyResolver";
    
    /** 模块管理器（用于获取所有模块的 Manifest） */
    private Map<String, ModuleManifest> manifests;
    
    /**
     * 构造函数。
     * 
     * @param manifests 模块清单映射表
     */
    public ModuleDependencyResolver(Map<String, ModuleManifest> manifests) {
        this.manifests = manifests != null ? manifests : new ConcurrentHashMap<>();
    }
    
    /**
     * 解析模块依赖（深度优先，检测循环依赖）。
     * 
     * @param manifest 模块清单
     * @return 依赖列表（按加载顺序排列，当前模块在最后）
     * @throws CircularDependencyException 检测到循环依赖
     */
    public List<String> resolveDependencies(ModuleManifest manifest) throws CircularDependencyException {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        resolveRecursive(manifest, result, visited);
        
        return result;
    }
    
    /**
     * 递归解析依赖。
     * 
     * @param manifest 当前模块清单
     * @param result 结果列表（输出参数）
     * @param visited 已访问模块集合（用于检测循环依赖）
     * @throws CircularDependencyException 检测到循环依赖
     */
    private void resolveRecursive(ModuleManifest manifest, List<String> result, Set<String> visited) 
            throws CircularDependencyException {
        
        String moduleId = manifest.getId();
        
        // 检测循环依赖
        if (visited.contains(moduleId)) {
            throw new CircularDependencyException(
                "检测到循环依赖: " + moduleId + " 已在依赖链中"
            );
        }
        
        // 标记为已访问
        visited.add(moduleId);
        
        // 递归解析依赖
        if (manifest.getDepends() != null) {
            for (String depId : manifest.getDepends()) {
                ModuleManifest depManifest = manifests.get(depId);
                if (depManifest == null) {
                    Log.w(TAG, "依赖不存在: " + depId + "，跳过");
                    continue;
                }
                
                resolveRecursive(depManifest, result, visited);
            }
        }
        
        // 添加到结果列表（当前模块在所有依赖之后）
        if (!result.contains(moduleId)) {
            result.add(moduleId);
        }
        
        // 移除访问标记（允许其他依赖链引用同一个模块）
        visited.remove(moduleId);
    }
    
    /**
     * 检测循环依赖（简化版，仅检查直接循环）。
     * 
     * @param manifest 模块清单
     * @return 是否存在循环依赖
     */
    public boolean hasCircularDependency(ModuleManifest manifest) {
        try {
            resolveDependencies(manifest);
            return false;
        } catch (CircularDependencyException e) {
            Log.e(TAG, "检测到循环依赖: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * 获取最高版本的依赖。
     * 
     * 如果多个模块依赖同一个库的不同版本，选择最高版本。
     * 
     * @param depId 依赖 ID
     * @return 最高版本的 ModuleManifest，如果依赖不存在返回 null
     */
    public ModuleManifest getHighestVersion(String depId) {
        ModuleManifest manifest = manifests.get(depId);
        
        if (manifest == null) {
            Log.w(TAG, "依赖不存在: " + depId);
            return null;
        }
        
        // TODO: 如果存在多个版本，选择最高版本
        // 当前假设每个依赖只有一个版本
        return manifest;
    }
    
    /**
     * 解析所有依赖（包括传递依赖）。
     * 
     * @param manifest 模块清单
     * @return 所有依赖的集合（包括传递依赖）
     */
    public java.util.Set<String> resolveAllDependencies(ModuleManifest manifest) {
        java.util.Set<String> allDeps = new HashSet<>();
        collectDependencies(manifest, allDeps);
        return allDeps;
    }
    
    /**
     * 递归收集依赖。
     */
    private void collectDependencies(ModuleManifest manifest, java.util.Set<String> collected) {
        if (manifest.getDepends() == null) {
            return;
        }
        
        for (String depId : manifest.getDepends()) {
            if (!collected.contains(depId)) {
                collected.add(depId);
                
                ModuleManifest depManifest = manifests.get(depId);
                if (depManifest != null) {
                    collectDependencies(depManifest, collected);
                }
            }
        }
    }
    
    /**
     * 循环依赖异常。
     * 
     * 当模块依赖关系图中检测到循环依赖时抛出。
     */
    public static class CircularDependencyException extends Exception {
        public CircularDependencyException(String message) {
            super(message);
        }
    }
}
