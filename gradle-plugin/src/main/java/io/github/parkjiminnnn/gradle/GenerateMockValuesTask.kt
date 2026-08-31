package io.github.parkjiminnnn.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * Fills in the slots that have no value yet.
 *
 * Run explicitly, never as part of a build. A build that produced values would need whatever
 * produces them on every machine that compiles, would rewrite a committed file behind the person
 * running it, and would make the same source compile to different Previews. Values are decided once
 * and committed; builds read them.
 */
abstract class GenerateMockValuesTask : DefaultTask() {
    // Deliberately not an @InputFile: Gradle snapshots those before the action runs and fails on a
    // missing one, and a manifest is missing exactly when someone runs this before ever building.
    // That case deserves the message below, not a Gradle error about a file it was told to expect.
    @get:Internal
    abstract val slotManifest: RegularFileProperty

    /**
     * Read as well as written: what is already decided is what makes a re-run cheap and a hand
     * edit permanent. Declared as an output because it is the thing this task produces.
     */
    @get:OutputFile
    abstract val mockValues: RegularFileProperty

    @get:Input
    @get:Optional
    @get:Option(option = "force", description = "Replace values that have already been decided.")
    abstract val force: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val baseUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val model: Property<String>

    @get:Input
    @get:Optional
    abstract val language: Property<String>

    // @Internal, not @Input: an input is hashed and can be recorded in build scans and caches, and
    // a credential has no business in either.
    @get:Internal
    abstract val apiKey: Property<String>

    // Set when the key was found somewhere that is normally committed. Carried as a message rather
    // than a flag so the task can say which file and what to do instead.
    @get:Internal
    abstract val rejectedApiKeyLocation: Property<String>

    init {
        group = "prevham"
        description = "Fills in mock values for the slots that have none."
        // Nothing about this is cacheable or skippable: the point of running it is to ask something
        // outside the build for answers it may give differently.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun generate() {
        rejectedApiKeyLocation.orNull?.let { error(it) }

        val manifest = slotManifest.get().asFile
        val slots = MockValueFiles.readSlots(manifest)
        if (slots.isEmpty()) {
            logger.lifecycle(
                "[PrevHam] no slots found in '$manifest'. " +
                    "Build the project first so the compiler can record them.",
            )
            return
        }

        val valuesFile = mockValues.get().asFile
        val existing = MockValueFiles.readValues(valuesFile)
        val undecided = slots.undecided(existing, force.getOrElse(false))
        logger.lifecycle("[PrevHam] ${slots.size} slot(s), ${undecided.size} without a value")
        if (undecided.isEmpty()) {
            logger.lifecycle("[PrevHam] nothing to do. Pass --force to replace decided values.")
            return
        }

        val generated = source().valuesFor(undecided)
        MockValueFiles.writeValues(valuesFile, existing.mergedWith(generated))
        logger.lifecycle("[PrevHam] wrote ${generated.size} value(s) to '$valuesFile'")
    }

    /**
     * Where values come from: an endpoint when one is configured, otherwise the scaffold.
     *
     * Falling back rather than failing is deliberate. Scaffolding the paths is useful on its own -
     * they are the part nobody can reasonably produce by hand - and someone who wants to write the
     * values themselves should not have to name a model to do it.
     */
    private fun source(): MockValueSource {
        val url = baseUrl.orNull
        val model = model.orNull
        if (url.isNullOrBlank() || model.isNullOrBlank()) {
            logger.lifecycle(
                "[PrevHam] no baseUrl/model configured - writing the slot paths with empty values. " +
                    "Set them in the prevham { } block to have them filled in.",
            )
            return PlaceholderMockValueSource
        }
        return OpenAiCompatibleMockValueSource(
            baseUrl = url,
            model = model,
            apiKey = apiKey.orNull,
            language = language.getOrElse("en"),
            logger = logger,
        )
    }
}
