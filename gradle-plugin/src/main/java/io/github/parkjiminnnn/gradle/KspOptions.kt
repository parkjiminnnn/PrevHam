package io.github.parkjiminnnn.gradle

import org.gradle.api.Project

/**
 * Sets the two options the compiler reads.
 *
 * Both are set from one place because the two sides have to agree on them: the compiler writes the
 * manifest and reads the values, the task reads the manifest and writes the values. Left to the
 * consumer that is the same path written twice, with nothing to notice when they drift.
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
    }

    private const val KSP_EXTENSION_NAME = "ksp"
    const val MOCK_VALUES_OPTION = "prevham.mockValues"
    const val SLOT_MANIFEST_OPTION = "prevham.slotManifest"
}
