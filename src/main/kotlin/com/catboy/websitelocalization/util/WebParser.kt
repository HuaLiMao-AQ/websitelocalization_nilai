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
import org.jsoup.nodes.Document

@Component
class WebParser(
    val translationProperties: TranslationProperties,
    val environment: Environment,
    private val pageRuleExecutor: PageRuleExecutor
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
    fun localization(body: String, requestPath: String): String {
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

        // 应用页面规则（按路径）
        try {
            pageRuleExecutor.applyRulesForPath(doc, requestPath)
        } catch (ex: Exception) {
            logger.warn("Apply page rules failed for path=$requestPath", ex)
        }

        return doc.html()
    }
}

// 规则执行扩展：按页面路径应用 DOM 修改
@Component
class PageRuleExecutor {
    private val mapper = jacksonObjectMapper()

    data class RuleAction(
        val selector: String,
        val type: String,
        val name: String? = null,
        val value: String? = null
    )

    data class RuleSet(
        val actions: List<RuleAction> = emptyList()
    )

    fun applyRulesForPath(document: Document, requestPath: String) {
        // 全局规则优先
        readRuleSet("/rules/global/rules.json")?.actions?.forEach { action ->
            applyAction(document, action)
        }
        // 页面规则
        val pageKey = resolvePageKey(requestPath)
        readRuleSet("/rules/pages/$pageKey/rules.json")?.actions?.forEach { action ->
            applyAction(document, action)
        }
    }

    private fun readRuleSet(resourcePath: String): RuleSet? {
        val resource = this::class.java.getResource(resourcePath) ?: return null
        val json = resource.readText(Charsets.UTF_8)
        return mapper.readValue(json)
    }

    private fun applyAction(document: Document, action: RuleAction) {
        val elements = document.select(action.selector)
        when (action.type.lowercase()) {
            "setattr" -> {
                val attrName = action.name ?: return
                val attrValue = action.value ?: return
                elements.forEach { el -> el.attr(attrName, attrValue) }
            }
            "settext" -> {
                val textValue = action.value ?: return
                elements.forEach { el -> el.text(textValue) }
            }
            "sethtml" -> {
                val htmlValue = action.value ?: return
                elements.forEach { el -> el.html(htmlValue) }
            }
            "prependhtml" -> {
                val htmlValue = action.value ?: return
                elements.forEach { el -> el.before(htmlValue) }
            }
            "appendhtml" -> {
                val htmlValue = action.value ?: return
                elements.forEach { el -> el.after(htmlValue) }
            }
            else -> {
                // 未知类型，忽略
            }
        }
    }

    private fun resolvePageKey(requestPath: String): String {
        val path = requestPath.ifBlank { "/" }
        return when {
            path == "/" || path == "/home" || path == "/node/1" -> "home"
            else -> {
                // 默认简单映射：/x 或 /x/... 映射为 pages/x/
                val seg = path.trim().removePrefix("/").takeWhile { it != '/' }
                seg.ifBlank { "home" }
            }
        }
    }
}
