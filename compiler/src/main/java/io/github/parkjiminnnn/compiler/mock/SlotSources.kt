package io.github.parkjiminnnn.compiler.mock

/**
 * Brings the slots recorded per source file up to date with one run of the processor.
 *
 * KSP processes incrementally: on a change it hands the processor only the files affected by it and
 * keeps the previous Previews of the rest. A manifest built from what the run met would describe that
 * subset - a third of the functions, a third of the slots - and the generation task and the
 * missing-value warning would then treat everything else as gone (issue #139). So the run replaces
 * only what it was given:
 *
 * - a file it [reprocessed] takes whatever the run [recorded] for it, which is nothing when its
 *   `@Prev` functions are gone
 * - a file it didn't is kept as it was, because KSP kept that file's Previews as they were - the slots
 *   are derived from the same traversal, so they are exactly as current as the Previews
 * - a file that no longer [exists] is dropped, since a deleted file is never handed to anyone
 *
 * A clean build reprocesses every file, so it comes out the same as replacing the manifest outright.
 */
internal object SlotSources {
    fun merge(
        previous: Map<String, List<MockSlot>>,
        reprocessed: Set<String>,
        recorded: Map<String, List<MockSlot>>,
        exists: (String) -> Boolean,
    ): Map<String, List<MockSlot>> {
        val kept = previous.filterKeys { it !in reprocessed && exists(it) }
        return (kept + recorded).filterValues { it.isNotEmpty() }
    }
}
