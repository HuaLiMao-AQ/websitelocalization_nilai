package com.catboy.websitelocalization.controller

import com.catboy.websitelocalization.service.CacheService
import com.catboy.websitelocalization.util.WebParser
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntity
import reactor.core.publisher.Mono
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.springframework.core.io.ClassPathResource

/**
 * 镜像控制器
 */
@CrossOrigin(origins = ["*"])
@RestController
class MirrorController(
    private val webClient: WebClient,
    private val webParser: WebParser,
    private val cacheService: CacheService,
    private val targetHost: String
) {
    // 日志记录器
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 跨域请求处理
     */
    @RequestMapping("/**", method = [RequestMethod.OPTIONS])
    fun options(): ResponseEntity<Void> {
        return ResponseEntity.ok()
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, OPTIONS")
            .header("Access-Control-Allow-Headers", "*")
            .build()
    }

    /**
     * 镜像请求处理
     */
    @GetMapping("/**")
    fun mirror(
        request: HttpServletRequest,
        @RequestHeader(HttpHeaders.USER_AGENT) userAgent: String
    ): Mono<ResponseEntity<Any>> {
        // 正确处理URL编码
        val decodedUri = URLDecoder.decode(request.requestURI, StandardCharsets.UTF_8)
        // 优先返回本地静态资源
        if (decodedUri.startsWith("/img/")) {
            val resourcePath = "static$decodedUri"
            val classPathResource = ClassPathResource(resourcePath)
            if (classPathResource.exists()) {
                val bytes = classPathResource.inputStream.use { it.readBytes() }
                val resource = ByteArrayResource(bytes)
                val contentType = when {
                    decodedUri.endsWith(".svg", true) -> "image/svg+xml"
                    decodedUri.endsWith(".png", true) -> "image/png"
                    decodedUri.endsWith(".jpg", true) || decodedUri.endsWith(".jpeg", true) -> "image/jpeg"
                    else -> MediaType.APPLICATION_OCTET_STREAM_VALUE
                }
                return Mono.just(
                    ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .contentLength(bytes.size.toLong())
                        .body(resource as Any)
                )
            }
        }
        val targetUrl = "$targetHost$decodedUri" +
                (if (request.queryString != null) "?${request.queryString}" else "")
        val cacheKey = generateCacheKey(request)

        return if (webParser.isHtmlRequest(request)) {
            val cachedContent = cacheService.getValidCache(cacheKey)
            if (cachedContent != null) {
                Mono.just(
                    ResponseEntity.status(HttpStatus.OK)
                        .contentType(MediaType.TEXT_HTML)
                        .body(cachedContent as Any)
                )
            } else {
                webClient.get()
                    .uri(targetUrl)
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .retrieve()
                    .onStatus({status -> status.is4xxClientError}) { clientResponse ->
                        // 记录错误但不抛出异常，让onErrorResume处理
                        logger.warn("Client error when fetching HTML content from $targetUrl: ${clientResponse.statusCode()}")
                        Mono.empty()
                    }
                    .onStatus({status -> status.is5xxServerError}) { clientResponse ->
                        // 记录错误但不抛出异常，让onErrorResume处理
                        logger.error("Server error when fetching HTML content from $targetUrl: ${clientResponse.statusCode()}")
                        Mono.empty()
                    }
                    .bodyToMono(String::class.java)
                    .map { body ->
                        val requestPath = request.requestURI
                        val localization = webParser.localization(body, requestPath)
                        cacheService.putCache(cacheKey, localization)
                        ResponseEntity.ok()
                            .contentType(MediaType.TEXT_HTML)
                            .body(localization as Any)
                    }
                    .onErrorResume(Exception::class.java) { ex ->
                        logger.error("Error fetching HTML content from $targetUrl", ex)
                        // 出错时返回重定向
                        Mono.just(
                            ResponseEntity.status(HttpStatus.FOUND)
                                .header(HttpHeaders.LOCATION, targetUrl)
                                .body(null as Any?)
                        )
                    }
            }
        } else {
            // 检查是否有缓存的非HTML资源
            val cachedBinaryContent = cacheService.getValidBinaryCache(cacheKey)
            if (cachedBinaryContent != null) {
                // 如果有缓存，直接返回缓存内容
                val cachedData = cachedBinaryContent.getData()
                val resource = ByteArrayResource(cachedData)
                val responseBuilder = ResponseEntity.ok()
                
                // 设置内容类型（如果存在）
                if (cachedBinaryContent.contentType != null) {
                    responseBuilder.header(HttpHeaders.CONTENT_TYPE, cachedBinaryContent.contentType)
                }
                
                return Mono.just(
                    responseBuilder
                        .contentLength(cachedData.size.toLong())
                        .body(resource as Any)
                )
            } else {
                // 如果没有缓存，从源服务器获取并缓存
                return webClient.get()
                    .uri(targetUrl) // 直接使用完整的URL
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .retrieve()
                    .onStatus({status -> status.is4xxClientError}) { clientResponse ->
                        // 记录错误但不抛出异常，让onErrorResume处理
                        logger.warn("Client error when fetching binary content from $targetUrl: ${clientResponse.statusCode()}")
                        Mono.empty()
                    }
                    .onStatus({status -> status.is5xxServerError}) { clientResponse ->
                        // 记录错误但不抛出异常，让onErrorResume处理
                        logger.error("Server error when fetching binary content from $targetUrl: ${clientResponse.statusCode()}")
                        Mono.empty()
                    }
                    .toEntity<ByteArray>()
                    .map { response ->
                        // 缓存响应内容
                        val contentType = response.headers.getFirst(HttpHeaders.CONTENT_TYPE)
                        cacheService.putBinaryCache(cacheKey, response.body!!, contentType)
                        
                        val resource = ByteArrayResource(response.body!!)
                        ResponseEntity.status(response.statusCode)
                            .headers(response.headers)
                            .contentLength(response.body!!.size.toLong())
                            .body(resource as Any)
                    }
                    .onErrorResume(Exception::class.java) { ex ->
                        logger.error("Error fetching binary content from $targetUrl", ex)
                        // 出错时返回重定向
                        Mono.just(
                            ResponseEntity.status(HttpStatus.FOUND)
                                .header(HttpHeaders.LOCATION, targetUrl)
                                .body(null as Any?)
                        )
                    }
            }
        }
    }

    /**
     * 处理POST请求
     */
    @PostMapping("/**")
    fun mirrorOfPost(
        request: HttpServletRequest,
        @RequestHeader(HttpHeaders.USER_AGENT) userAgent: String,
        @RequestBody(required = false) requestBody: String?
    ): Mono<ResponseEntity<String?>?> {
        val targetUrl = "$targetHost${request.requestURI}" +
                (if (request.queryString != null) "?${request.queryString}" else "")

        // 发送POST请求
        return webClient.post()
            .uri(targetUrl)
            .header(HttpHeaders.USER_AGENT, userAgent)
            .bodyValue(requestBody ?: "")
            .retrieve()
            .toEntity<String>()
            .map { response ->
                ResponseEntity.status(response.statusCode)
                    .headers(response.headers)
                    .body(response.body)
            }
    }
    
    /**
     * 生成缓存键，包含查询参数以确保唯一性
     */
    private fun generateCacheKey(request: HttpServletRequest): String {
        return request.requestURI + 
               (if (request.queryString != null) "?${request.queryString}" else "")
    }
}
