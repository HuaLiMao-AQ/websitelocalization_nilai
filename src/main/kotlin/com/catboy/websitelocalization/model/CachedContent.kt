package com.catboy.websitelocalization.model

/**
 * 缓存内容
 * @param content 缓存内容
 * @param timestamp 缓存时间戳
 * @param contentType 内容类型
 * @param isBinary 是否为二进制内容
 */
data class CachedContent(
    val content: String,
    val timestamp: Long,
    val contentType: String? = null,
    val isBinary: Boolean = false
) {
    // 获取二进制数据
    fun getData(): ByteArray = if (isBinary) {
        content.toByteArray(Charsets.ISO_8859_1)
    } else {
        content.toByteArray()
    }
    
    // 判断是否为文本内容
    fun isTextContent(): Boolean = !isBinary
}
