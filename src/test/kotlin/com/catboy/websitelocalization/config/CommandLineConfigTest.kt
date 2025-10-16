package com.catboy.websitelocalization.config

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = [
    "translator.provider=DEEPL",
    "translator.deepl.api.url=https://api.deepl.com/v2/translate",
    "translator.deepl.api.key=test-key"
])
class CommandLineConfigTest {

    @Test
    fun testCommandLineProperties() {
        // 这里可以添加实际的测试逻辑
        assert(true)
    }
}