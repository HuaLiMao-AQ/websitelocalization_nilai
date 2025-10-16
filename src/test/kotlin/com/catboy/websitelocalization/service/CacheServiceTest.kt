package com.catboy.websitelocalization.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class CacheServiceTest {

    @Autowired
    private lateinit var cacheService: CacheService

    @Test
    fun testCachePutAndGet() {
        cacheService.clearCache()
        
        val key = "/test"
        val content = "test content"
        
        cacheService.putCache(key, content)
        val cachedContent = cacheService.getValidCache(key)
        
        assertEquals(content, cachedContent)
    }

    @Test
    fun testCacheSizeLimit() {
        cacheService.clearCache()
        
        // 添加超过限制的缓存项
        for (i in 1..1100) {
            cacheService.putCache("/test$i", "content$i")
        }
        
        // 检查缓存大小是否在限制范围内
        assertTrue(cacheService.getCacheSize() <= 1000)
    }
}