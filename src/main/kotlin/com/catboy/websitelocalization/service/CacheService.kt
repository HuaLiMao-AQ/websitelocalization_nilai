package com.catboy.websitelocalization.service

import com.catboy.websitelocalization.model.CachedContent
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * 缓存服务
 */
@Service
class CacheService {
    companion object {
        private const val MAX_CACHE_SIZE = 1000
        private val cacheTimeout = Duration.ofMinutes(30)
    }

    // 缓存
    private val cacheUri = ConcurrentHashMap<String, CachedContent>()

    /**
     * 获取有效的缓存内容
     * @param key 缓存键
     * @return 有效的缓存内容，如果不存在或已过期则返回null
     */
    fun getValidCache(key: String): String? {
        val cached = cacheUri[key]
        return if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheTimeout.toMillis()) {
            if (cached.isTextContent()) {
                cached.content
            } else {
                null // 对于二进制内容，我们使用另一个方法
            }
        } else {
            cacheUri.remove(key) // 清除过期缓存
            null
        }
    }

    /**
     * 获取有效的二进制缓存内容
     * @param key 缓存键
     * @return 有效的缓存内容，如果不存在或已过期则返回null
     */
    fun getValidBinaryCache(key: String): CachedContent? {
        val cached = cacheUri[key]
        return if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheTimeout.toMillis()) {
            if (!cached.isTextContent()) {
                cached
            } else {
                null // 对于文本内容，我们使用另一个方法
            }
        } else {
            cacheUri.remove(key) // 清除过期缓存
            null
        }
    }

    /**
     * 存储文本内容到缓存
     * @param key 缓存键
     * @param content 缓存内容
     */
    fun putCache(key: String, content: String) {
        // 如果缓存已满，移除最旧的条目
        if (cacheUri.size >= MAX_CACHE_SIZE) {
            val oldestKey = cacheUri.entries.minByOrNull { it.value.timestamp }?.key
            if (oldestKey != null) {
                cacheUri.remove(oldestKey)
            }
        }
        
        cacheUri[key] = CachedContent(content, System.currentTimeMillis())
    }
    
    /**
     * 存储二进制内容到缓存
     * @param key 缓存键
     * @param data 缓存的二进制数据
     * @param contentType 内容类型
     */
    fun putBinaryCache(key: String, data: ByteArray, contentType: String?) {
        // 如果缓存已满，移除最旧的条目
        if (cacheUri.size >= MAX_CACHE_SIZE) {
            val oldestKey = cacheUri.entries.minByOrNull { it.value.timestamp }?.key
            if (oldestKey != null) {
                cacheUri.remove(oldestKey)
            }
        }
        
        cacheUri[key] = CachedContent(String(data, Charsets.ISO_8859_1), System.currentTimeMillis(), contentType, true)
    }
    
    /**
     * 清除所有缓存
     */
    fun clearCache() {
        cacheUri.clear()
    }
    
    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Int {
        return cacheUri.size
    }
}
