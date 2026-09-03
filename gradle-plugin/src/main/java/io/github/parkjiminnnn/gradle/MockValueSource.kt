package io.github.parkjiminnnn.gradle

/**
 * A slot a value can be supplied for, as read from the manifest the compiler writes.
 *
 * [owner] and [name] arrive separately rather than being split out of [slot]: a nested declaration
 * puts dots on both sides of the boundary, so the joined path can't be taken apart again.
 */
internal data class MockValueSlot(
    val slot: String,
    val owner: String,
    val name: String,
    val type: String,
)

/**
 * Where values come from.
 *
 * The seam the pipeline is built around. Everything either side of it - reading the manifest,
 * subtracting what already has a value, merging, writing - is the same whoever answers, so all of it
 * can be built and tested without one.
 */
internal fun interface MockValueSource {
    fun valuesFor(slots: List<MockValueSlot>): Map<String, String>
}

/**
 * Answers every slot with a blank, so the file is scaffolded with the paths and nothing else.
 *
 * The paths are the part a person can't reasonably produce: they are fully qualified, a wrong one
 * fails silently, and a real model has hundreds. Filling them in by hand from there is ordinary
 * work; finding them is not.
 *
 * A blank reads as "not decided yet" - the compiler falls back to its default for one - so a
 * scaffolded file changes no Preview until someone writes into it.
 */
internal object PlaceholderMockValueSource : MockValueSource {
    override fun valuesFor(slots: List<MockValueSlot>): Map<String, String> = slots.associate { it.slot to "" }
}
