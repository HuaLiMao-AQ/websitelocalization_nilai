package com.catboy.websitelocalization.util

import com.catboy.websitelocalization.config.TranslationProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import jakarta.servlet.http.HttpServletRequest
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component

@Component
class WebParser(
    val translationProperties: TranslationProperties,
    val environment: Environment
) {
    // 日志记录器
    private val logger = LoggerFactory.getLogger(this::class.java)

    // 翻译消息
    @Transient
    val messages: MutableMap<String, String> = mutableMapOf()

    /**
     * 加载翻译文件
     */
    @PostConstruct
    fun load() {
        val mapper = jacksonObjectMapper()
        val rawMap = mutableMapOf<String, String>()

        translationProperties.filePath.forEach {
            val fileContent = this::class.java.getResource(it)!!.readText(charset = Charsets.UTF_8)
            rawMap.putAll(mapper.readValue(fileContent))
        }
        
        messages.putAll(rawMap.mapKeys { (k,_) ->
            Parser.unescapeEntities(k, false)
                .replace("\u00A0", " ")
                .trim()
        }.mapValues { (_,v) ->
            Parser.unescapeEntities(v, false)
                .replace("\u00A0", " ")
                .trim()
        })
        logger.info("A total of ${messages.size} local record is loaded")
    }

    /**
     * 判断是否为HTML请求
     * @param request 请求对象
     * @return 是否为HTML请求
     */
    fun isHtmlRequest(request: HttpServletRequest): Boolean {
        var isHtml = request
            .getHeader(HttpHeaders.ACCEPT)
            ?.contains("text/html") ?: false

        // 忽略文件请求
        if (isHtml) {
            if (request.requestURI.contains("sites/default/files")) {
                isHtml = false
            }
        }

        return isHtml
    }

    /**
     * 进行翻译
     * @param body 请求体
     * @return 翻译后的内容
     */
    fun localization(body: String): String {
        val doc = Jsoup.parse(body)
        doc.head().traverse { node, _ ->
            if (node is TextNode) {
                val text = Parser.unescapeEntities(node.text(), false)
                    .replace("\u00A0", " ")
                    .trim()
                val translatedText = messages[text]
                node.text(translatedText ?: text)
            }
        }
        doc.body().traverse { node, _ ->
            if (node is TextNode) {
                val text = Parser.unescapeEntities(node.text(), false)
                    .replace("\u00A0", " ")
                    .trim()
                val translatedText = messages[text]
                node.text(translatedText ?: text)
            }
        }

        return doc.html()
    }
}
