package io.github.parkjiminnnn.gradle

import groovy.json.JsonSlurper
import java.io.File

/**
 * Reading and writing the two files the pipeline moves between.
 *
 * Groovy's JSON, which Gradle bundles, rather than a declared dependency: anything added here lands
 * on every consumer's buildscript classpath, and this plugin exists to avoid putting things there.
 */
internal object MockValueFiles {
    /** The slots the compiler recorded, or empty when it hasn't written a manifest yet. */
    fun readSlots(file: File): List<MockValueSlot> {
        if (!file.isFile) return emptyList()
        val root = JsonSlurper().parseText(file.readText()) as? Map<*, *> ?: return emptyList()
        val slots = root["slots"] as? List<*> ?: return emptyList()
        return slots.mapNotNull { entry ->
            val fields = entry as? Map<*, *> ?: return@mapNotNull null
            MockValueSlot(
                slot = fields["slot"] as? String ?: return@mapNotNull null,
                owner = fields["owner"] as? String ?: return@mapNotNull null,
                name = fields["name"] as? String ?: return@mapNotNull null,
                type = fields["type"] as? String ?: return@mapNotNull null,
            )
        }
    }

    /** The values already decided, or empty when the file doesn't exist yet. */
    fun readValues(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val root = JsonSlurper().parseText(file.readText()) as? Map<*, *> ?: return emptyMap()
        return root.entries
            .mapNotNull { (key, value) ->
                val slot = key as? String ?: return@mapNotNull null
                val text = value as? String ?: return@mapNotNull null
                slot to text
            }.toMap()
    }

    /**
     * Writes the values sorted, so a re-run produces a diff of what changed rather than a reshuffle.
     *
     * Written here rather than with JsonOutput, which escapes every non-ASCII character:
     * `"제 1회 대학 음악제"` comes back as `"\uc81c 1\ud68c …"`. That is valid JSON and reads back
     * correctly, but this file exists to be opened and edited by hand - a value nobody can read is a
     * value nobody will correct. JSON is UTF-8, so the characters need no escaping at all.
     */
    fun writeValues(
        file: File,
        values: Map<String, String>,
    ) {
        file.parentFile?.mkdirs()
        val body =
            values.toSortedMap().entries.joinToString(",\n") { (slot, value) ->
                "    ${slot.asJsonString()}: ${value.asJsonString()}"
            }
        file.writeText(if (body.isEmpty()) "{}\n" else "{\n$body\n}\n")
    }

    // Only what JSON requires: the two structural characters and the control range. Everything else,
    // including every non-ASCII character, is written as itself.
    private fun String.asJsonString(): String =
        buildString(length + 2) {
            append('"')
            this@asJsonString.forEach { character ->
                when {
                    character == '"' -> append("\\\"")
                    character == '\\' -> append("\\\\")
                    character == '\n' -> append("\\n")
                    character == '\r' -> append("\\r")
                    character == '\t' -> append("\\t")
                    character < ' ' -> append("\\u%04x".format(character.code))
                    else -> append(character)
                }
            }
            append('"')
        }
}

/**
 * The slots to ask about: those with nothing decided for them yet.
 *
 * A value already present is left alone, so a hand-written answer survives every later run - which
 * is the whole reason values live in a file rather than in generated code. [force] asks about
 * everything instead, for when the existing answers are the thing being replaced.
 *
 * A blank counts as undecided: the scaffold writes blanks, so a second run without [force] should
 * still be able to fill them.
 */
internal fun List<MockValueSlot>.undecided(
    values: Map<String, String>,
    force: Boolean,
): List<MockValueSlot> = if (force) this else filter { values[it.slot].isNullOrBlank() }

/**
 * Existing values, plus the new ones, with existing winning.
 *
 * Deliberately not the other way round: a source answering a slot that already has a value must not
 * overwrite it. [undecided] already filters those out, so this is the second of two guards on the
 * same rule - the one that holds even if a source answers more than it was asked.
 */
internal fun Map<String, String>.mergedWith(generated: Map<String, String>): Map<String, String> = generated + this
