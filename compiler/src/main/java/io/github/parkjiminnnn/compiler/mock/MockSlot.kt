package io.github.parkjiminnnn.compiler.mock

/**
 * A place a value goes, identified by where it is declared.
 *
 * `com.example.Festival.festivalName` rather than `kotlin.String`, because type alone cannot choose
 * a value - `Festival.name` and `User.name` are both `String` and want different answers.
 *
 * [owner] and [name] are kept apart rather than derived from [path]: a nested declaration puts dots
 * on both sides of the split (`com.example.Outer.Inner.name`), so the boundary can't be recovered
 * from the joined string. The processor knows it exactly, so it records it.
 */
internal data class MockSlot(
    val owner: String,
    val name: String,
    val type: String,
) {
    val path: String get() = "$owner.$name"
}

/**
 * The slots met while generating, in the order they were filled.
 *
 * Shared across the whole round rather than copied on descent - it describes one compilation, not
 * one path through it - and appended to only while generating, never while deciding, so [MockContext.canMock]
 * stays free of side effects.
 *
 * Only the processor can produce this list. Whether `Festival.festivalName` is a slot at all depends
 * on PrevHam reaching `Festival`, and that is decided by cycle detection, the stub budget, typealias
 * resolution and which generator claims a type - none of which is visible in the source text. A tool
 * outside the compilation would have to reimplement all of it to guess.
 */
internal class SlotRecorder {
    private val slots = LinkedHashSet<MockSlot>()

    fun record(slot: MockSlot) {
        slots += slot
    }

    /** Sorted, so the manifest is stable across builds and chunking downstream is reproducible. */
    fun recorded(): List<MockSlot> = slots.sortedBy { it.path }
}
