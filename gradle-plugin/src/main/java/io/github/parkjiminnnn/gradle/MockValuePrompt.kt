package io.github.parkjiminnnn.gradle

/**
 * Turns slots into something a model can answer.
 *
 * Slots are grouped by their declaring type because the siblings are the context. `festivalName`
 * alone says little; `festivalName` beside `universityName`, under a type called `Festival`, says it
 * is a university festival app - and grouping also keeps the values within one type consistent with
 * each other rather than answered in isolation.
 *
 * ### Why the keys are short
 *
 * Slots are identified internally by their fully qualified path, but asking a model to echo
 * `com.daedan.festabook.domain.model.Festival.festivalName` back exactly is asking it to do
 * transcription rather than the work. It fails at it: measured against a real project, the only
 * value that survived validation was the one with the shortest key, and the two longer ones came
 * back altered enough to be dropped. Long keys also spend output tokens - the priced ones - on text
 * that carries no answer.
 *
 * So a request uses `Type.property`, and the full path is restored from the request itself. Chunking
 * keeps simple type names unique within a request, so the short form always maps back to exactly one
 * slot.
 */
internal object MockValuePrompt {
    fun system(language: String): String =
        """
        You write placeholder data for Jetpack Compose Preview functions.

        For each property you are given, invent one short, realistic value that a real screen of this
        kind would show. Use the property name and its declaring type to decide what the value should
        be: a property called `universityName` on a type called `Festival` is the name of an actual
        university, not the word "university" or a placeholder like "example".

        Rules:
        - Write values in $language.
        - A property typed Int, Long, Short, Byte, Double or Float takes a bare number and nothing
          else: no units, no thousands separators, no words. `450`, not `"about 450 people"`.
        - Keep values short enough to fit a phone screen, but long enough to be realistic. Do not pad
          to a fixed length.
        - Values are shown to a developer previewing a screen. They are never real user data, and
          must not look like a real person's name, address, phone number, or account.
        - Reply with a JSON object mapping each key exactly as given to its value, and nothing else.
        - Every key you were given must appear exactly once. Do not add keys.

        Example request:
        type: Festival
          Festival.festivalName : String
          Festival.expectedVisitors : Int

        Example reply:
        {"Festival.festivalName": "…", "Festival.expectedVisitors": "12000"}
        """.trimIndent()

    fun user(slots: List<MockValueSlot>): String =
        slots
            .groupBy { it.owner }
            .entries
            .joinToString("\n\n") { (owner, owned) ->
                buildString {
                    append("type: ").append(owner.simpleName())
                    owned.forEach { append("\n  ").append(it.shortKey()).append(" : ").append(it.type.simpleName()) }
                }
            }

    /** The key a reply is expected to use, e.g. `Festival.festivalName`. */
    fun MockValueSlot.shortKey(): String = "${owner.simpleName()}.$name"

    /**
     * Slots split into request-sized groups.
     *
     * Two rules, both about keeping a request answerable. A type is never split across requests,
     * because a type answered in halves loses the sibling context that makes its values agree. And
     * two types with the same simple name never share a request, because the reply is keyed by that
     * name and would otherwise be ambiguous - `Festival.name` could belong to either.
     */
    fun chunk(
        slots: List<MockValueSlot>,
        maxSlots: Int,
    ): List<List<MockValueSlot>> {
        val chunks = mutableListOf<List<MockValueSlot>>()
        var current = mutableListOf<MockValueSlot>()
        var simpleNames = mutableSetOf<String>()

        slots.groupBy { it.owner }.entries.forEach { (owner, owned) ->
            val simpleName = owner.simpleName()
            val full = current.isNotEmpty() && current.size + owned.size > maxSlots
            if (full || simpleName in simpleNames) {
                chunks += current
                current = mutableListOf()
                simpleNames = mutableSetOf()
            }
            current += owned
            simpleNames += simpleName
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    // The declaring type's own name, without its package or enclosing types - `Outer.Inner` keeps
    // both, since dropping the outer one would make two nested types indistinguishable.
    private fun String.simpleName(): String = substringAfterLast('.')
}
