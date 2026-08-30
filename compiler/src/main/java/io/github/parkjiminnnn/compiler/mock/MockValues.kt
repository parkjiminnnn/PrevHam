package io.github.parkjiminnnn.compiler.mock

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Values to use for named slots instead of the generators' defaults.
 *
 * A slot is a place a value goes, identified by where it is *declared* rather than by its type -
 * `com.example.Festival.festivalName`. Type alone isn't enough to choose a value: `Festival.name`
 * and `User.name` are both `String` and want different answers.
 *
 * The file is written by the generation task and committed, so a build reads it and never produces
 * it. That keeps generation out of the build: no network, no credentials, the same Preview from the
 * same source, and a diff a human can read and edit - with those edits surviving the next run.
 *
 * Absent, unreadable, or malformed, this is [EMPTY] and every generator behaves exactly as it did
 * before. A value file can make a Preview better; it can never stop one being generated.
 */
internal class MockValues(
    private val values: Map<String, String>,
) {
    operator fun get(slot: String): String? = values[slot]

    val isEmpty: Boolean get() = values.isEmpty()

    companion object {
        val EMPTY = MockValues(emptyMap())

        /**
         * Reads a flat `{"slot": "value"}` file, or [EMPTY] with the reason when it can't be read.
         *
         * Parsing is deliberately total: a hand-edited file with a stray comma should degrade the
         * Preview to its defaults, not fail the compilation round.
         */
        fun from(file: File): Result<MockValues> =
            runCatching {
                val text = file.readText()
                if (text.isBlank()) return@runCatching EMPTY
                MockValues(Json.decodeFromString(SERIALIZER, text))
            }

        private val SERIALIZER = MapSerializer(String.serializer(), String.serializer())
    }
}
