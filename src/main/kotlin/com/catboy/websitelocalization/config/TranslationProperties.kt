package com.catboy.websitelocalization.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * 翻译配置属性
 */
@Configuration
@ConfigurationProperties(prefix = "translation")
class TranslationProperties {
    var filePath: List<String> = emptyList()
}
