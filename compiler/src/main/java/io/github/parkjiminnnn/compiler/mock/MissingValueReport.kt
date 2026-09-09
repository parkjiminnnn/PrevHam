package io.github.parkjiminnnn.compiler.mock

/**
 * The warning for slots the value file has nothing for.
 *
 * Adding a field is the ordinary case and the one with no signal. The build succeeds, the Preview
 * renders, and the new field reads `"mock"` beside fields that read like real data - nothing says
 * the generation task should be run again. The cost lands on the feature rather than on any one
 * Preview: a file that covered a project when it was written covers a shrinking part of it a month
 * later, and there is no moment where that becomes visible.
 *
 * Comparing is all this needs. The recorder already holds every slot the round met and the value
 * file already holds what is decided, so the difference between them is the answer.
 */
internal object MissingValueReport {
    // Enough to act on without becoming the build output. Adopting this mid-project leaves every
    // slot undecided on the first build, and a wall of paths helps nobody - there, the count and the
    // task name are the parts worth reading.
    private const val MAX_LISTED = 10

    /** The message for [paths], or null when there is nothing to say. */
    fun message(paths: List<String>): String? {
        if (paths.isEmpty()) return null
        return buildString {
            append("[PrevHam] ${paths.size} slot(s) have no mock value:")
            paths.take(MAX_LISTED).forEach { append("\n  $it") }
            val remaining = paths.size - MAX_LISTED
            if (remaining > 0) append("\n  ... and $remaining more")
            // Unindented, and after a blank line: at the list's own indent the way out reads as one
            // more path.
            append("\n\nRun ./gradlew prevhamGenerateMockValues to fill them in.")
            append("\nSet warnOnMissingValues = false in the prevham { } block to stop hearing about it.")
        }
    }
}
