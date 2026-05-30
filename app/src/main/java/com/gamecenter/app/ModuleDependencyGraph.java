package com.gamecenter.app;

import android.util.Log;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模块依赖关系图。
 * 
 * 负责解析模块依赖关系：
 * - 添加/删除依赖关系
 * - 计算模块加载顺序（拓扑排序）
 * - 检测循环依赖
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class ModuleDependencyGraph {
    
    private static final String TAG = "ModuleDependencyGraph";
    
    /** 依赖关系存储（模块 ID -> 依赖的模块 ID 列表） */
    private final Map<String, List<String>> dependencies;
    
    /** 反向依赖关系（模块 ID -> 依赖该模块的模块 ID 列表） */
    private final Map<String, List<String>> reverseDependencies;
    
    /**
     * 默认构造函数。
     */
    public ModuleDependencyGraph() {
        this.dependencies = new HashMap<>();
        this.reverseDependencies = new HashMap<>();
    }
    
    /**
     * 添加模块依赖关系。
     * 
     * @param moduleId 模块 ID
     * @param dependsOn 依赖的模块 ID 列表
     */
    public void addDependency(@NonNull String moduleId, 
                              @NonNull List<String> dependsOn) {
        if (moduleId == null || moduleId.isEmpty()) {
            Log.e(TAG, "moduleId 为空");
            return;
        }
        
        if (dependsOn == null) {
            Log.e(TAG, "dependsOn 为 null");
            return;
        }
        
        // 存储依赖关系
        dependencies.put(moduleId, new ArrayList<>(dependsOn));
        
        // 存储反向依赖关系
        for (String dep : dependsOn) {
            reverseDependencies.computeIfAbsent(dep, k -> new ArrayList<>()).add(moduleId);
        }
        
        Log.d(TAG, "依赖关系已添加: " + moduleId + " 依赖于 " + dependsOn);
    }
    
    /**
     * 移除模块依赖关系。
     * 
     * @param moduleId 模块 ID
     */
    public void removeDependency(@NonNull String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) {
            return;
        }
        
        // 移除依赖关系
        List<String> deps = dependencies.remove(moduleId);
        
        // 移除反向依赖关系
        if (deps != null) {
            for (String dep : deps) {
                List<String> reverseDeps = reverseDependencies.get(dep);
                if (reverseDeps != null) {
                    reverseDeps.remove(moduleId);
                }
            }
        }
        
        // 移除其他模块对该模块的依赖
        for (List<String> depList : dependencies.values()) {
            depList.remove(moduleId);
        }
        
        // 移除反向依赖
        reverseDependencies.remove(moduleId);
        
        Log.d(TAG, "依赖关系已移除: " + moduleId);
    }
    
    /**
     * 获取模块加载顺序（拓扑排序）。
     * 
     * @param moduleId 目标模块 ID
     * @return 加载顺序列表（从依赖到目标）
     * @throws CircularDependencyException 检测到循环依赖时抛出
     */
    @NonNull
    public List<String> getLoadOrder(@NonNull String moduleId) 
            throws CircularDependencyException {
        if (moduleId == null || moduleId.isEmpty()) {
            Log.e(TAG, "moduleId 为空");
            return new ArrayList<>();
        }
        
        // 检查循环依赖
        if (hasCircularDependency()) {
            throw new CircularDependencyException("检测到循环依赖");
        }
        
        // 拓扑排序
        List<String> loadOrder = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        // DFS 遍历依赖
        topologicalSort(moduleId, visited, loadOrder);
        
        return loadOrder;
    }
    
    /**
     * 拓扑排序（深度优先搜索）。
     */
    private void topologicalSort(@NonNull String moduleId, 
                                 @NonNull Set<String> visited, 
                                 @NonNull List<String> result) {
        if (visited.contains(moduleId)) {
            return;
        }
        
        visited.add(moduleId);
        
        // 先处理依赖
        List<String> deps = dependencies.get(moduleId);
        if (deps != null) {
            for (String dep : deps) {
                topologicalSort(dep, visited, result);
            }
        }
        
        // 再添加自己
        if (!result.contains(moduleId)) {
            result.add(moduleId);
        }
    }
    
    /**
     * 检查是否存在循环依赖。
     * 
     * @return 存在循环依赖返回 true，否则返回 false
     */
    public boolean hasCircularDependency() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String moduleId : dependencies.keySet()) {
            if (hasCircularDependencyDFS(moduleId, visited, recursionStack)) {
                Log.e(TAG, "检测到循环依赖，起始模块: " + moduleId);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 深度优先搜索检测循环依赖。
     */
    private boolean hasCircularDependencyDFS(@NonNull String moduleId, 
                                             @NonNull Set<String> visited, 
                                             @NonNull Set<String> recursionStack) {
        if (recursionStack.contains(moduleId)) {
            return true; // 检测到循环
        }
        
        if (visited.contains(moduleId)) {
            return false; // 已访问过，无循环
        }
        
        visited.add(moduleId);
        recursionStack.add(moduleId);
        
        List<String> deps = dependencies.get(moduleId);
        if (deps != null) {
            for (String dep : deps) {
                if (hasCircularDependencyDFS(dep, visited, recursionStack)) {
                    return true;
                }
            }
        }
        
        recursionStack.remove(moduleId);
        return false;
    }
    
    /**
     * 获取指定模块的所有依赖（递归）。
     * 
     * @param moduleId 模块 ID
     * @return 依赖模块 ID 集合
     */
    @NonNull
    public Set<String> getAllDependencies(@NonNull String moduleId) {
        Set<String> allDeps = new HashSet<>();
        collectDependencies(moduleId, allDeps);
        return allDeps;
    }
    
    /**
     * 递归收集依赖。
     */
    private void collectDependencies(@NonNull String moduleId, 
                                    @NonNull Set<String> collected) {
        List<String> deps = dependencies.get(moduleId);
        if (deps == null) {
            return;
        }
        
        for (String dep : deps) {
            if (!collected.contains(dep)) {
                collected.add(dep);
                collectDependencies(dep, collected);
            }
        }
    }
    
    /**
     * 获取依赖指定模块的所有模块（反向依赖）。
     * 
     * @param moduleId 模块 ID
     * @return 依赖该模块的模块 ID 列表
     */
    @NonNull
    public List<String> getDependents(@NonNull String moduleId) {
        List<String> deps = reverseDependencies.get(moduleId);
        return deps != null ? new ArrayList<>(deps) : new ArrayList<>();
    }
    
    /**
     * 检查两个模块是否存在依赖关系。
     * 
     * @param moduleId 模块 ID
     * @param dependsOn 可能被依赖的模块 ID
     * @return 存在依赖返回 true，否则返回 false
     */
    public boolean hasDependency(@NonNull String moduleId, 
                                @NonNull String dependsOn) {
        Set<String> allDeps = getAllDependencies(moduleId);
        return allDeps.contains(dependsOn);
    }
    
    /**
     * 清空依赖关系图。
     */
    public void clear() {
        dependencies.clear();
        reverseDependencies.clear();
        Log.d(TAG, "依赖关系图已清空");
    }
    
    /**
     * 获取所有已注册的模块 ID 列表。
     * 
     * @return 模块 ID 列表
     */
    @NonNull
    public List<String> getAllModules() {
        Set<String> allModules = new HashSet<>(dependencies.keySet());
        allModules.addAll(reverseDependencies.keySet());
        return new ArrayList<>(allModules);
    }

    /**
     * 打印依赖关系图（调试用）。
     */
    public void printGraph() {
        Log.d(TAG, "===== 依赖关系图 =====");
        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
            Log.d(TAG, entry.getKey() + " 依赖于 " + entry.getValue());
        }
        Log.d(TAG, "=======================");
    }

    /**
     * 循环依赖异常。
     *
     * 当模块依赖关系图中检测到循环依赖时抛出。
     */
    public static class CircularDependencyException extends Exception {
        public CircularDependencyException(@NonNull String message) {
            super(message);
        }
    }
}
