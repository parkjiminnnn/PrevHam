package io.github.parkjiminnnn.gradle

import groovy.json.JsonOutput
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

    /** Writes the values sorted, so a re-run produces a diff of what changed rather than a reshuffle. */
    fun writeValues(
        file: File,
        values: Map<String, String>,
    ) {
        file.parentFile?.mkdirs()
        file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(values.toSortedMap())) + "\n")
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
