package io.github.parkjiminnnn.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * The `prevham { }` block.
 *
 * Both paths have defaults, and both are set on the KSP processor from here - the manifest is
 * written by the compiler and read by the task, the values the other way round, so the two have to
 * agree on where they are. Leaving that to the consumer would mean writing the same path twice in
 * two different places and having neither complain when they drifted.
 */
abstract class PrevHamExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        /**
         * Where decided values are read from and written to.
         *
         * A source file rather than a build output: it is the one part of this that is worth
         * keeping, worth reviewing in a diff, and worth editing by hand.
         */
        val mockValues: RegularFileProperty = objects.fileProperty()

        /**
         * Where the compiler records the slots a value could be supplied for.
         *
         * A build output. It is rewritten on every build and describes what the compilation just
         * did, so there is nothing in it to keep.
         */
        val slotManifest: RegularFileProperty = objects.fileProperty()

        /**
         * The OpenAI-compatible chat endpoint to ask for values, e.g.
         * `https://integrate.api.nvidia.com/v1`.
         *
         * Required rather than defaulted, and PrevHam takes no position on which one. A default
         * would endorse a provider, and would be half a convenience anyway - a key and a model are
         * needed regardless, so nothing works without configuration either way. Unset, the task
         * scaffolds the slot paths and leaves the values to be written by hand.
         */
        val baseUrl: Property<String> = objects.property(String::class.java)

        /** The model to ask, as that endpoint names it. */
        val model: Property<String> = objects.property(String::class.java)

        /**
         * The language to write values in.
         *
         * Configured rather than inferred: guessing from identifiers is unreliable, and a wrong
         * guess produces values in the wrong language for a whole project.
         */
        val language: Property<String> = objects.property(String::class.java)

        /**
         * Whether a build says which slots have no value yet.
         *
         * On by default, because the people it exists for are the ones already using a value file
         * and they are exactly the ones who would never think to switch it on. Adding a field is the
         * ordinary case and the one with no signal otherwise - the build succeeds, the Preview
         * renders, and the new field reads `"mock"` beside fields that read like real data.
         *
         * A project that has never run the generation task hears nothing regardless: with no value
         * file there is nothing to be missing from.
         */
        val warnOnMissingValues: Property<Boolean> = objects.property(Boolean::class.java)
    }
