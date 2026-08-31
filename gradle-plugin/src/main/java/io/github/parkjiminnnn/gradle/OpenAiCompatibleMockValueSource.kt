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

    /**
     * One chunk, retried while the failure looks like the endpoint rather than the request.
     *
     * A free or shared endpoint queues, and a queued request reads as a timeout. Nothing is retried
     * on a 4xx: an expired key or an unknown model will not fix itself, and asking again only makes
     * the wait longer before the same message.
     */
    private fun ask(slots: List<MockValueSlot>): Map<String, String> {
        val body = requestBody(slots)
        repeat(MAX_ATTEMPTS) { attempt ->
            val last = attempt == MAX_ATTEMPTS - 1
            val response =
                runCatching { client.send(request(body), HttpResponse.BodyHandlers.ofString()) }
                    .getOrElse { failure ->
                        if (last) {
                            logger.warn("[PrevHam] request to '$baseUrl' failed: ${failure.message}")
                            return emptyMap()
                        }
                        logger.lifecycle(
                            "[PrevHam] ${failure.message} - retrying (${attempt + 2} of $MAX_ATTEMPTS)",
                        )
                        Thread.sleep(RETRY_BACKOFF_MILLIS * (attempt + 1))
                        return@repeat
                    }

            val status = response.statusCode()
            if (status in 200..299) return validated(parseValues(response.body()), slots)

            // The body carries the reason - an expired key, an unknown model, a rate limit - and
            // guessing at which is less useful than showing it.
            val excerpt = response.body().take(ERROR_EXCERPT)
            if (last || status !in RETRYABLE_STATUSES) {
                logger.warn("[PrevHam] '$baseUrl' answered $status: $excerpt")
                return emptyMap()
            }
            logger.lifecycle("[PrevHam] answered $status - retrying (${attempt + 2} of $MAX_ATTEMPTS)")
            Thread.sleep(RETRY_BACKOFF_MILLIS * (attempt + 1))
        }
        return emptyMap()
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
     * Answers to what was asked, restored to their full slot paths.
     *
     * The reply is keyed by the short `Type.property` form the request used, and chunking has
     * already made those unique within a request, so each maps back to exactly one slot. A key that
     * maps to none was invented or altered, and a blank is not an answer; neither can be told from a
     * good value once written to the file, so both are dropped - and counted, because a source
     * losing most of its replies is worth knowing about before the file looks mysteriously
     * incomplete.
     */
    private fun validated(
        values: Map<String, String>,
        asked: List<MockValueSlot>,
    ): Map<String, String> {
        val byShortKey = asked.associateBy { with(MockValuePrompt) { it.shortKey() } }
        val kept =
            values
                .mapNotNull { (key, value) ->
                    val slot = byShortKey[key] ?: return@mapNotNull null
                    if (value.isBlank()) null else slot.slot to value
                }.toMap()
        val dropped = values.size - kept.size
        if (dropped > 0) {
            // The keys, not just the count: a reply is dropped for using keys we did not ask for, and
            // which ones it used instead is the only thing that says whether the prompt, the
            // validation, or the model is at fault. Counting alone sends you guessing.
            logger.warn(
                "[PrevHam] dropped $dropped unusable value(s) from a reply covering ${asked.size} slot(s)\n" +
                    "  asked for: ${byShortKey.keys.sorted()}\n" +
                    "  answered:  ${values.keys.sorted()}",
            )
        }
        return kept
    }

    private companion object {
        // Small enough that one bad reply costs little, large enough that a type's siblings travel
        // together. Types are never split, so a large one may exceed this on its own.
        const val MAX_SLOTS_PER_REQUEST = 40
        const val ERROR_EXCERPT = 300
        const val MAX_ATTEMPTS = 3
        const val RETRY_BACKOFF_MILLIS = 2_000L

        // A free or shared endpoint queues rather than refusing, so a slow answer is the normal
        // case rather than a broken one. This is a task somebody runs deliberately and rarely -
        // waiting is cheaper than failing and being run again.
        val REQUEST_TIMEOUT: Duration = Duration.ofMinutes(5)

        // Retried because the endpoint is busy or briefly unwell, not because the request is wrong.
        val RETRYABLE_STATUSES = setOf(408, 429, 500, 502, 503, 504)

        fun defaultClient(): HttpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build()
    }
}
