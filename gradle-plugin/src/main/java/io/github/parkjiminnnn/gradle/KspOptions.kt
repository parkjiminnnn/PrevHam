package io.github.parkjiminnnn.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity

/**
 * Sets the options the compiler reads.
 *
 * The two paths are set from one place because the two sides have to agree on them: the compiler
 * writes the manifest and reads the values, the task reads the manifest and writes the values. Left
 * to the consumer that is the same path written twice, with nothing to notice when they drift.
 *
 * ### Why this reaches the extension by name
 *
 * The KSP Gradle plugin is `compileOnly` here on purpose - bundling it would put its version, and so
 * a Kotlin version, on every consumer's buildscript, which is the pinning this plugin exists to
 * avoid. Referring to `KspExtension` as a type costs more than it looks:
 *
 * - Gradle inspects every method of a plugin class when it decorates it, and a lambda passed to
 *   `extensions.configure(KspExtension::class.java)` compiles to a method taking one - so applying
 *   the plugin failed with `NoClassDefFoundError` even in builds that had KSP.
 * - Moving it to a separately loaded class fixes that but not the second problem: under Gradle
 *   TestKit the plugin under test is injected into its own classloader, which cannot see KSP at all.
 *   The typed version would work in a real consumer build and be untestable here, which is worse
 *   than not having it.
 *
 * Reaching the object by name and calling through reflection crosses both boundaries. This is a
 * build plugin configuring another build plugin, not the compile-time generation the compiler module
 * keeps free of reflection.
 */
internal object KspOptions {
    fun set(
        project: Project,
        extension: PrevHamExtension,
    ) {
        val ksp = project.extensions.findByName(KSP_EXTENSION_NAME) ?: return
        val arg =
            runCatching { ksp.javaClass.getMethod("arg", String::class.java, String::class.java) }
                .getOrElse {
                    // A KSP whose extension no longer takes arguments this way. Warned rather than
                    // failed: every Preview still generates, they just use their default values.
                    project.logger.warn(
                        "[PrevHam] could not set KSP options on the '$KSP_EXTENSION_NAME' extension " +
                            "(${ksp.javaClass.name}). Mock values will not be read. " +
                            "Set them yourself with ksp { arg(\"$MOCK_VALUES_OPTION\", ...) }.",
                    )
                    return
                }
        arg.invoke(
            ksp,
            MOCK_VALUES_OPTION,
            extension.mockValues
                .get()
                .asFile.absolutePath,
        )
        arg.invoke(
            ksp,
            SLOT_MANIFEST_OPTION,
            extension.slotManifest
                .get()
                .asFile.absolutePath,
        )
        arg.invoke(
            ksp,
            WARN_ON_MISSING_VALUES_OPTION,
            extension.warnOnMissingValues.getOrElse(true).toString(),
        )
        declareValueFileAsInput(project, extension)
    }

    /**
     * Makes the value file an input of every KSP task, so changing it re-runs the processor.
     *
     * The option above hands the processor a path, and Gradle fingerprints that option as the
     * string it is. The path never changes, so a build after the file changed found every input
     * identical and skipped KSP - the Preview kept its old values, including straight after
     * prevhamGenerateMockValues had written new ones (issue #133). Gradle cannot see what a task
     * reads while it runs; anything read that way has to be declared.
     *
     * - `files`, not `file(...).optional()`: the file does not exist until values are first
     *   generated, and a project that never uses the feature must build exactly as before.
     *   `optional()` only lets the property go unset - a path that is set must exist, and the build
     *   fails on it. A missing entry in a file collection is allowed, and fingerprinted as missing, so
     *   the file appearing later still counts as a change.
     * - `PathSensitivity.NONE`: the content is what decides, not where the file sits, so two
     *   checkouts at different paths agree.
     * - Matched by task name rather than type, because the KSP Gradle plugin is `compileOnly` here -
     *   see the class comment. `configureEach` attaches this as tasks are created instead of
     *   creating them, so configuration is not slowed.
     *
     * The other direction is deliberately kept: with nothing changed, KSP stays up to date. Forcing
     * it to run on every build would also make the values arrive, and would slow every build of
     * every consumer to do it.
     */
    private fun declareValueFileAsInput(
        project: Project,
        extension: PrevHamExtension,
    ) {
        project.tasks.configureEach { task ->
            if (!task.name.startsWith(KSP_EXTENSION_NAME) || !task.name.endsWith("Kotlin")) return@configureEach
            task.inputs
                .files(extension.mockValues)
                .withPathSensitivity(PathSensitivity.NONE)
                .withPropertyName(MOCK_VALUES_INPUT)
        }
    }

    private const val KSP_EXTENSION_NAME = "ksp"
    const val MOCK_VALUES_OPTION = "prevham.mockValues"
    const val SLOT_MANIFEST_OPTION = "prevham.slotManifest"
    const val WARN_ON_MISSING_VALUES_OPTION = "prevham.warnOnMissingValues"

    // What Gradle prints when the file is why KSP re-ran - worth reading as a name.
    private const val MOCK_VALUES_INPUT = "prevhamMockValues"
}
