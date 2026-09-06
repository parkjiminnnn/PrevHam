package io.github.parkjiminnnn.compiler

/** A `@Prev` that produced no Preview, and why. */
internal data class SkippedPrev(
    val name: String,
    val reason: String,
)

/**
 * What the round did with the `@Prev` annotations it found.
 *
 * Counted rather than derived, because the three outcomes are decided in three different places and
 * only the processor sees all of them.
 */
internal class RoundTally {
    var found = 0
        private set
    var generated = 0
        private set

    /** Reported as an error and counted here too, so the three outcomes add up to [found]. */
    var failed = 0
        private set

    private val skippedPreviews = mutableListOf<SkippedPrev>()
    val skipped: List<SkippedPrev> get() = skippedPreviews

    fun found() = found++

    fun generated() = generated++

    fun failed() = failed++

    fun skipped(
        name: String,
        reason: String,
    ) {
        skippedPreviews += SkippedPrev(name, reason)
    }

    /** Whether this is worth seeing without `--info`. */
    val hasProblems: Boolean get() = skippedPreviews.isNotEmpty() || failed > 0
}

/**
 * One line saying what `@Prev` produced, with the skipped ones under it.
 *
 * A build gave no summary before, so there was no telling a project with few Previews from one where
 * most were being skipped - and those need different responses. The per-composable warnings said
 * which and why, but one per skip, scattered through however many hundred lines of unrelated output
 * a build produces, is easy to miss and impossible to count.
 *
 * So they are gathered here instead of being reported where they happen. Nothing is lost by moving
 * them: KSP prints these without a source location either way, so the standalone warnings pointed at
 * nothing the reader could click.
 */
internal object RoundReport {
    // Same threshold as the missing-value warning, for the same reason: past a point the list stops
    // being something to act on and the count is what matters.
    private const val MAX_LISTED = 10

    /** The message, or null when the round has nothing to report. */
    fun message(tally: RoundTally): String? {
        // A project not using PrevHam should hear nothing from it.
        if (tally.found == 0) return null
        val counts =
            buildList {
                add("${tally.generated} generated")
                if (tally.skipped.isNotEmpty()) add("${tally.skipped.size} skipped")
                if (tally.failed > 0) add("${tally.failed} failed")
            }
        return buildString {
            append("[PrevHam] ${tally.found} @Prev found: ${counts.joinToString(", ")}")
            if (tally.skipped.isEmpty()) return@buildString
            append(":")
            tally.skipped.take(MAX_LISTED).forEach { append("\n  ${it.name}: ${it.reason}") }
            val remaining = tally.skipped.size - MAX_LISTED
            if (remaining > 0) append("\n  ... and $remaining more")
        }
    }
}
