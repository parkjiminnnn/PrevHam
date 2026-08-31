package io.github.parkjiminnnn.gradle

/**
 * Turns slots into something a model can answer.
 *
 * Slots are grouped by their declaring type because the siblings are the context. `festivalName`
 * alone says little; `festivalName` beside `universityName`, under a type named
 * `com.daedan.festabook.domain.model.Festival`, says it is a university festival app - and grouping
 * also keeps the values within one type consistent with each other rather than answered in
 * isolation.
 *
 * The reply is asked for in the shape of the value file itself: a flat object from slot path to
 * value. Anything more structured is more for a model to get wrong, and a wrong key is indis-
 * tinguishable from a wrong value once it arrives.
 */
internal object MockValuePrompt {
    fun system(language: String): String =
        """
        You write placeholder data for Jetpack Compose Preview functions.

        For each property you are given, invent one short, realistic value that a real screen of this
        kind would show. Use the property name and its declaring type to decide what the value should
        be: a property called `universityName` on a type called `Festival` is the name of a
        university, not the word "university".

        Rules:
        - Write values in $language.
        - Keep values short enough to fit a phone screen, but long enough to be realistic. Do not pad
          to a fixed length.
        - Values are shown to a developer previewing a screen. They are never real user data, and
          must not look like a real person's name, address, phone number, or account.
        - Reply with a JSON object mapping each key exactly as given to its value, and nothing else.
        - Every key you were given must appear exactly once. Do not add keys.

        Example reply:
        {"com.example.app.Festival.festivalName": "…", "com.example.app.Festival.universityName": "…"}
        """.trimIndent()

    fun user(slots: List<MockValueSlot>): String =
        slots
            .groupBy { it.owner }
            .entries
            .joinToString("\n\n") { (owner, owned) ->
                buildString {
                    append("type: ").append(owner)
                    owned.forEach { slot ->
                        append("\n  ").append(slot.slot).append(" : ").append(slot.type)
                    }
                }
            }

    /**
     * Slots split into request-sized groups, without separating a type across two of them.
     *
     * A type answered in halves loses the sibling context that makes the values agree, so a type is
     * kept whole even where that takes a chunk past [maxSlots].
     */
    fun chunk(
        slots: List<MockValueSlot>,
        maxSlots: Int,
    ): List<List<MockValueSlot>> {
        val chunks = mutableListOf<List<MockValueSlot>>()
        var current = mutableListOf<MockValueSlot>()
        slots.groupBy { it.owner }.values.forEach { owned ->
            if (current.isNotEmpty() && current.size + owned.size > maxSlots) {
                chunks += current
                current = mutableListOf()
            }
            current += owned
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }
}
