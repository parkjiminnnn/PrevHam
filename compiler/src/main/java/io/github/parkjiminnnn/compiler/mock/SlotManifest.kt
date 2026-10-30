package io.github.parkjiminnnn.compiler.mock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * The slots a compilation met, written out so tooling outside the build can see them.
 *
 * This is a build output and not a source of truth: it belongs under `build/`, never in version
 * control. The committed file is the *value* file, which this exists to help produce - the generation
 * task subtracts the values already present from the slots listed here and asks only about the
 * difference, so a re-run costs what has been added rather than everything.
 *
 * `owner`, `name` and `type` are written alongside `slot` rather than left to be parsed back out of
 * it. A nested declaration puts dots on both sides of the split, and the prompt built downstream
 * groups by owner and states the type, so both are needed in a form that doesn't require guessing.
 *
 * `sources` says which source file each slot came from, and is what lets an incremental build update
 * the file instead of replacing it - see [SlotSources]. `slots` is the union of it, kept as the one
 * thing the generation task reads, so the task doesn't change shape with the processor.
 */
internal object SlotManifest {
    fun write(
        file: File,
        sources: Map<String, List<MockSlot>>,
    ) {
        file.parentFile?.mkdirs()
        file.writeText(JSON.encodeToString(JsonObject.serializer(), sources.toJson()))
    }

    /**
     * The slots each source file contributed, as the manifest at [file] last recorded them.
     *
     * Empty when there is no manifest, when it can't be parsed, and when it was written before
     * `sources` existed. Each of those leaves nothing to keep, which is the state a clean build starts
     * from - and a build that reprocesses everything rewrites the file whole either way.
     */
    fun read(file: File): Map<String, List<MockSlot>> {
        if (!file.isFile) return emptyMap()
        val root = runCatching { JSON.parseToJsonElement(file.readText()) as? JsonObject }.getOrNull() ?: return emptyMap()
        val slotsByPath =
            (root["slots"] as? JsonArray)
                .orEmpty()
                .mapNotNull { it.toSlot() }
                .associateBy { it.path }
        val sources = root["sources"] as? JsonObject ?: return emptyMap()
        return sources
            .mapValues { (_, paths) ->
                (paths as? JsonArray)
                    .orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull()?.let(slotsByPath::get) }
            }.filterValues { it.isNotEmpty() }
    }

    private fun Map<String, List<MockSlot>>.toJson(): JsonObject =
        buildJsonObject {
            putJsonArray("slots") {
                values.flatten().distinct().sortedBy { it.path }.forEach { slot ->
                    addJsonObject {
                        put("slot", slot.path)
                        put("owner", slot.owner)
                        put("name", slot.name)
                        put("type", slot.type)
                    }
                }
            }
            putJsonObject("sources") {
                toSortedMap().forEach { (source, slots) ->
                    putJsonArray(source) {
                        slots
                            .map { it.path }
                            .distinct()
                            .sorted()
                            .forEach { add(JsonPrimitive(it)) }
                    }
                }
            }
        }

    private fun JsonElement.toSlot(): MockSlot? {
        val fields = this as? JsonObject ?: return null
        return MockSlot(
            owner = fields.string("owner") ?: return null,
            name = fields.string("name") ?: return null,
            type = fields.string("type") ?: return null,
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull()

    private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else null

    private val JSON = Json { prettyPrint = true }
}
