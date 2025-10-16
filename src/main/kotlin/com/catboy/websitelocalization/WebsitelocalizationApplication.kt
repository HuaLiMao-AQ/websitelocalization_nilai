package com.catboy.websitelocalization

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@SpringBootApplication
class WebsitelocalizationApplication {
    // 获取目标网站地址
    @Value("\${mirror.target_url}")
    private lateinit var _targetHost: String

    val targetHost: String
        @Bean get() = _targetHost
    // 创建WebClient
    @Bean
    fun webClient(): WebClient = WebClient.builder()
        .baseUrl(targetHost)
        .exchangeStrategies(ExchangeStrategies.builder()
            .codecs {
                it.defaultCodecs()
                    .maxInMemorySize(1024 * 1024 * 10)
            }.build())
        .build()
}

fun main(args: Array<String>) {
    runApplication<WebsitelocalizationApplication>(*args)
}
