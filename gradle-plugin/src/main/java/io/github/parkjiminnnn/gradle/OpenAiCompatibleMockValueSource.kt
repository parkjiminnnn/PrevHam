package io.github.parkjiminnnn.gradle

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.logging.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Asks an OpenAI-compatible chat endpoint for values.
 *
 * One implementation covers NVIDIA NIM, OpenAI, Ollama, Groq and anything else speaking the same
 * shape - only [baseUrl] and [model] change. PrevHam takes no position on which: the endpoint is
 * required rather than defaulted, so nobody's build calls a service they didn't name.
 *
 * Written against the JDK's own HTTP client and Gradle's bundled JSON rather than a provider SDK.
 * Anything this module depends on lands on every consumer's buildscript classpath, and a Preview
 * tool has no business putting an AI vendor's library there.
 *
 * Nothing here can fail a build. A refused request, a malformed reply, a key that isn't a key - all
 * of it warns and leaves the slots undecided, which reads exactly like never having run the task.
 */
internal class OpenAiCompatibleMockValueSource(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String?,
    private val language: String,
    private val logger: Logger,
    private val client: HttpClient = defaultClient(),
) : MockValueSource {
    override fun valuesFor(slots: List<MockValueSlot>): Map<String, String> {
        warnIfKeyLooksMissing()
        return MockValuePrompt
            .chunk(slots, MAX_SLOTS_PER_REQUEST)
            .flatMap { chunk -> ask(chunk).entries }
            .associate { it.key to it.value }
    }

    /**
     * Says so when there is no key and the endpoint looks like it wants one.
     *
     * Not an error: a local endpoint - Ollama and anything else on this machine - needs none, and
     * refusing to run without a key would rule that out. But sending nothing to a hosted endpoint
     * gets a 401 back, and "answered 401" is a worse thing to read than "you have not set a key"
     * when the second is what happened.
     */
    private fun warnIfKeyLooksMissing() {
        if (!apiKey.isNullOrBlank() || isLocal()) return
        logger.warn(
            "[PrevHam] no API key found, and '$baseUrl' is not a local endpoint. " +
                "Set ${ApiKey.GRADLE_PROPERTY} in local.properties, or ${ApiKey.ENVIRONMENT} in the environment.",
        )
    }

    private fun isLocal(): Boolean =
        runCatching { URI.create(baseUrl).host }
            .getOrNull()
            .orEmpty()
            .let { it == "localhost" || it.startsWith("127.") || it == "::1" || it == "0.0.0.0" }

    private fun ask(slots: List<MockValueSlot>): Map<String, String> {
        val body = requestBody(slots)
        val response =
            runCatching { client.send(request(body), HttpResponse.BodyHandlers.ofString()) }
                .getOrElse { failure ->
                    logger.warn("[PrevHam] request to '$baseUrl' failed: ${failure.message}")
                    return emptyMap()
                }

        if (response.statusCode() !in 200..299) {
            // The body carries the reason - an expired key, an unknown model, a rate limit - and
            // guessing at which is less useful than showing it.
            logger.warn(
                "[PrevHam] '$baseUrl' answered ${response.statusCode()}: ${response.body().take(ERROR_EXCERPT)}",
            )
            return emptyMap()
        }

        return validated(parseValues(response.body()), slots)
    }

    private fun request(body: String): HttpRequest =
        HttpRequest
            .newBuilder(URI.create("${baseUrl.trimEnd('/')}/chat/completions"))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .apply { if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey") }
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

    private fun requestBody(slots: List<MockValueSlot>): String =
        JsonOutput.toJson(
            mapOf(
                "model" to model,
                "messages" to
                    listOf(
                        mapOf("role" to "system", "content" to MockValuePrompt.system(language)),
                        mapOf("role" to "user", "content" to MockValuePrompt.user(slots)),
                    ),
                // Asked for rather than relied on: support varies by provider and by model, and a
                // reply that ignores it is handled the same as any other unusable one.
                "response_format" to mapOf("type" to "json_object"),
            ),
        )

    /** The model's reply, dug out of the envelope and parsed - or empty if either step fails. */
    private fun parseValues(body: String): Map<String, String> =
        runCatching {
            val envelope = JsonSlurper().parseText(body) as? Map<*, *> ?: return emptyMap()
            val choices = envelope["choices"] as? List<*> ?: return emptyMap()
            val message = (choices.firstOrNull() as? Map<*, *>)?.get("message") as? Map<*, *> ?: return emptyMap()
            val content = message["content"] as? String ?: return emptyMap()
            val values = JsonSlurper().parseText(content) as? Map<*, *> ?: return emptyMap()
            values.entries
                .mapNotNull { (key, value) ->
                    val slot = key as? String ?: return@mapNotNull null
                    val text = value as? String ?: return@mapNotNull null
                    slot to text
                }.toMap()
        }.getOrElse { failure ->
            logger.warn("[PrevHam] could not read the reply from '$baseUrl': ${failure.message}")
            emptyMap()
        }

    /**
     * Keeps only answers to what was asked.
     *
     * A model may invent a key, garble one of the long paths, or answer with a blank. None of those
     * can be told apart from a good answer once written to the file, so they are dropped here - and
     * counted, because a source dropping most of its replies is worth knowing about before the file
     * looks mysteriously incomplete.
     */
    private fun validated(
        values: Map<String, String>,
        asked: List<MockValueSlot>,
    ): Map<String, String> {
        val wanted = asked.mapTo(mutableSetOf()) { it.slot }
        val kept = values.filterKeys { it in wanted }.filterValues { it.isNotBlank() }
        val dropped = values.size - kept.size
        if (dropped > 0) {
            logger.warn("[PrevHam] dropped $dropped unusable value(s) from a reply covering ${asked.size} slot(s)")
        }
        return kept
    }

    private companion object {
        // Small enough that one bad reply costs little, large enough that a type's siblings travel
        // together. Types are never split, so a large one may exceed this on its own.
        const val MAX_SLOTS_PER_REQUEST = 40
        const val ERROR_EXCERPT = 300
        val REQUEST_TIMEOUT: Duration = Duration.ofMinutes(2)

        fun defaultClient(): HttpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build()
    }
}
