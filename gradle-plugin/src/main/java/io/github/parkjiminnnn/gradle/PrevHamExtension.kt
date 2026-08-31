package io.github.parkjiminnnn.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
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
    }
