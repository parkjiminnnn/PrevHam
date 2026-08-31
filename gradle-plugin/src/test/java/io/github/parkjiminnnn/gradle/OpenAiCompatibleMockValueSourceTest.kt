package io.github.parkjiminnnn.gradle

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.gradle.api.logging.Logging
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

// Runs the real HttpClient against a server in this process, so the request that goes out and the
// reply that comes back are the ones the code actually builds and parses. Nothing here needs a
// provider, a key, or a network - what a real endpoint adds is whether the values are any good.
class OpenAiCompatibleMockValueSourceTest {
    private lateinit var server: HttpServer
    private val received = mutableListOf<String>()
    private var reply: (HttpExchange) -> Pair<Int, String> = { 200 to chatReply("{}") }

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            received += exchange.requestBody.readBytes().decodeToString()
            received += exchange.requestHeaders.getFirst("Authorization").orEmpty()
            val (status, body) = reply(exchange)
            exchange.sendResponseHeaders(status, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun baseUrl() = "http://127.0.0.1:${server.address.port}/v1"

    private fun chatReply(content: String) =
        groovy.json.JsonOutput.toJson(
            mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content)))),
        )

    private fun source(
        url: String = baseUrl(),
        apiKey: String? = "test-key",
    ) = OpenAiCompatibleMockValueSource(url, "some-model", apiKey, "ko", Logging.getLogger("test"))

    private fun slot(name: String) = MockValueSlot("com.a.Festival.$name", "com.a.Festival", name, "kotlin.String")

    // Gradle's Logger is a wide interface and this needs one method from it, so a proxy is less
    // code than a stub and cannot fall behind the interface.
    private fun collectingLogger(into: MutableList<String>): org.gradle.api.logging.Logger =
        java.lang.reflect.Proxy.newProxyInstance(
            org.gradle.api.logging.Logger::class.java.classLoader,
            arrayOf(org.gradle.api.logging.Logger::class.java),
        ) { _, method, args ->
            if (method.name == "warn") args?.firstOrNull()?.let { into += it.toString() }
            if (method.returnType == Boolean::class.javaPrimitiveType) false else null
        } as org.gradle.api.logging.Logger

    private fun requestBody() = received.first()

    private fun authorization() = received[1]

    @Test
    fun `returns the values the endpoint answered`() {
        reply = { 200 to chatReply("""{"com.a.Festival.name": "2026 대동제"}""") }

        val values = source().valuesFor(listOf(slot("name")))

        assertEquals(mapOf("com.a.Festival.name" to "2026 대동제"), values)
    }

    @Test
    fun `sends the model, the prompt and a JSON reply format`() {
        source().valuesFor(listOf(slot("name")))

        val body = requestBody()
        assertTrue(body, body.contains("\"model\":\"some-model\""))
        assertTrue(body, body.contains("com.a.Festival.name"))
        assertTrue(body, body.contains("json_object"))
        assertTrue(body, body.contains("ko"))
    }

    @Test
    fun `sends the key as a bearer token`() {
        source().valuesFor(listOf(slot("name")))

        assertEquals("Bearer test-key", authorization())
    }

    @Test
    fun `does not warn about a missing key for a local endpoint`() {
        // Ollama and anything else on this machine needs none, so requiring one would rule it out.
        val warnings = mutableListOf<String>()
        val logger = collectingLogger(warnings)

        OpenAiCompatibleMockValueSource(baseUrl(), "some-model", null, "ko", logger)
            .valuesFor(listOf(slot("name")))

        assertTrue(warnings.toString(), warnings.none { it.contains("no API key found") })
    }

    @Test
    fun `warns about a missing key for a hosted endpoint`() {
        // Otherwise the first sign is "answered 401", which is a worse thing to read than the truth.
        val warnings = mutableListOf<String>()
        val logger = collectingLogger(warnings)

        OpenAiCompatibleMockValueSource("https://example.invalid/v1", "some-model", null, "ko", logger)
            .valuesFor(listOf(slot("name")))

        assertTrue(warnings.toString(), warnings.any { it.contains("no API key found") })
        assertTrue(warnings.toString(), warnings.any { it.contains("local.properties") })
    }

    @Test
    fun `sends no authorization when there is no key`() {
        // A local endpoint like Ollama needs none, and sending an empty bearer is worse than none.
        source(apiKey = null).valuesFor(listOf(slot("name")))

        assertEquals("", authorization())
    }

    @Test
    fun `gives up quietly when the endpoint refuses`() {
        // An expired key, an unknown model, a rate limit. The slots stay undecided, which reads
        // exactly like never having run the task - not like a broken build.
        reply = { 401 to """{"error": "invalid api key"}""" }

        assertEquals(emptyMap<String, String>(), source().valuesFor(listOf(slot("name"))))
    }

    @Test
    fun `gives up quietly when nothing is listening`() {
        val values = source(url = "http://127.0.0.1:1/v1").valuesFor(listOf(slot("name")))

        assertEquals(emptyMap<String, String>(), values)
    }

    @Test
    fun `gives up quietly when the envelope is not what it expects`() {
        reply = { 200 to """{"unexpected": true}""" }

        assertEquals(emptyMap<String, String>(), source().valuesFor(listOf(slot("name"))))
    }

    @Test
    fun `gives up quietly when the reply is not JSON`() {
        // A model ignoring the requested format, which is the common failure and not an exceptional
        // one - support for json_object varies by provider and by model.
        reply = { 200 to chatReply("Sure! Here are some values:") }

        assertEquals(emptyMap<String, String>(), source().valuesFor(listOf(slot("name"))))
    }

    @Test
    fun `drops a key it never asked about`() {
        // A garbled path is indistinguishable from a good one once written to the file.
        reply = {
            200 to chatReply("""{"com.a.Festival.name": "kept", "com.a.Festival.invented": "dropped"}""")
        }

        val values = source().valuesFor(listOf(slot("name")))

        assertEquals("kept", values["com.a.Festival.name"])
        assertNull(values["com.a.Festival.invented"])
    }

    @Test
    fun `drops a blank answer`() {
        reply = { 200 to chatReply("""{"com.a.Festival.name": "  "}""") }

        assertEquals(emptyMap<String, String>(), source().valuesFor(listOf(slot("name"))))
    }

    @Test
    fun `splits a large set across several requests`() {
        reply = { 200 to chatReply("{}") }

        source().valuesFor((1..90).map { MockValueSlot("com.a.T$it.p", "com.a.T$it", "p", "kotlin.String") })

        // Two entries are recorded per request - the body and the header.
        assertTrue("${received.size / 2} request(s)", received.size / 2 > 1)
    }
}
