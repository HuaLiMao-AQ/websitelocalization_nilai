package com.catboy.websitelocalization.util

import com.catboy.websitelocalization.config.TranslationProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest
class WebParserTest {

    @Autowired
    private lateinit var webParser: WebParser

    @MockBean
    private lateinit var translationProperties: TranslationProperties

    @MockBean
    private lateinit var translationService: TranslationService

    @Test
    fun testLocalizationWithExistingTranslation() {
        val html = "<html><body><p>Hello World</p></body></html>"
        // 添加一个已知的翻译
        webParser.messages["Hello World"] = "你好世界"
        
        val localizedHtml = webParser.localization(html)
        
        assertTrue(localizedHtml.contains("你好世界"))
    }

    @Test
    fun testLocalizationWithoutTranslation() {
        val html = "<html><body><p>Goodbye World</p></body></html>"
        
        // 模拟翻译服务返回翻译结果
        `when`(translationService.translate("Goodbye World", "en", "zh"))
            .thenReturn(reactor.core.publisher.Mono.just("再见世界"))
        
        val localizedHtml = webParser.localization(html)
        
        assertTrue(localizedHtml.contains("再见世界"))
    }
}
