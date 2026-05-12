package com.gamecenter.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

object BitmapCache {
    private const val MAX_CACHE_SIZE = 10 * 1024 * 1024 // 10MB
    private val cache = ConcurrentHashMap<String, WeakReference<Bitmap>>()
    private var currentSize = 0L
    
    @JvmStatic
    fun get(key: String): Bitmap? = cache[key]?.get()
    
    @JvmStatic
    fun put(key: String, bitmap: Bitmap) {
        val size = bitmap.byteCount.toLong()
        if (currentSize + size > MAX_CACHE_SIZE) {
            evictOldest()
        }
        cache[key] = WeakReference(bitmap)
        currentSize += size
    }
    
    @JvmStatic
    fun remove(key: String) {
        cache[key]?.get()?.let {
            currentSize -= it.byteCount.toLong()
        }
        cache.remove(key)
    }
    
    @JvmStatic
    fun clear() {
        cache.values.forEach { it.get()?.recycle() }
        cache.clear()
        currentSize = 0
    }
    
    private fun evictOldest() {
        val iterator = cache.entries.iterator()
        while (iterator.hasNext() && currentSize > MAX_CACHE_SIZE / 2) {
            val entry = iterator.next()
            entry.value.get()?.let {
                currentSize -= it.byteCount.toLong()
                it.recycle()
            }
            iterator.remove()
        }
    }
    
    @JvmStatic
    fun loadSampled(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)
        
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        
        return BitmapFactory.decodeFile(path, options)
    }
    
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (width: Int, height: Int) = options.outWidth to options.outHeight
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight
                && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}

object MemoryMonitor {
    private val runtime = Runtime.getRuntime()
    
    @JvmStatic
    fun getUsedMemory(): Long = runtime.totalMemory() - runtime.freeMemory()
    
    @JvmStatic
    fun getMaxMemory(): Long = runtime.maxMemory()
    
    @JvmStatic
    fun getAvailableMemory(): Long = getMaxMemory() - getUsedMemory()
    
    @JvmStatic
    fun getMemoryUsagePercent(): Int = (getUsedMemory() * 100 / getMaxMemory()).toInt()
    
    @JvmStatic
    fun isLowMemory(): Boolean = getMemoryUsagePercent() > 80
    
    @JvmStatic
    fun gc() {
        System.gc()
        AppLog.i("MemoryMonitor: GC triggered, used=${getUsedMemory() / 1024 / 1024}MB")
    }
}
