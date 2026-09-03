package io.github.parkjiminnnn.compiler.mock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File

/**
 * The slots a compilation met, written out so tooling outside the build can see them.
 *
 * This is a build output and not a source of truth: it is rewritten on every round and belongs under
 * `build/`, never in version control. The committed file is the *value* file, which this exists to
 * help produce - the generation task subtracts the values already present from the slots listed here
 * and asks only about the difference, so a re-run costs what has been added rather than everything.
 *
 * `owner`, `name` and `type` are written alongside `slot` rather than left to be parsed back out of
 * it. A nested declaration puts dots on both sides of the split, and the prompt built downstream
 * groups by owner and states the type, so both are needed in a form that doesn't require guessing.
 */
internal object SlotManifest {
    fun write(
        file: File,
        slots: List<MockSlot>,
    ) {
        file.parentFile?.mkdirs()
        file.writeText(JSON.encodeToString(JsonObject.serializer(), slots.toJson()))
    }

    private fun List<MockSlot>.toJson(): JsonObject =
        buildJsonObject {
            putJsonArray("slots") {
                forEach { slot ->
                    addJsonObject {
                        put("slot", slot.path)
                        put("owner", slot.owner)
                        put("name", slot.name)
                        put("type", slot.type)
                    }
                }
            }
        }

    private val JSON = Json { prettyPrint = true }
}
