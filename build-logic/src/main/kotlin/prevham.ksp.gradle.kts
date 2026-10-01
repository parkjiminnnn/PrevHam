// Applied by id rather than through a plugins {} block, and that is the point.
//
// A plugins {} block here makes build-logic compile against the KSP Gradle plugin, and build-logic
// compiles with Gradle's embedded Kotlin - 2.0.21 on Gradle 8.13 - which cannot read KSP 2.3's
// metadata (issue #73). A string id needs KSP only at runtime.
//
// The plugin still has to arrive through build-logic rather than being declared by each module.
// Gradle gives projects with identical plugin classpaths one classloader, and a module resolving
// KSP on its own gets a classpath - and so a classloader - of its own. The publishing plugin shares
// one build service across every published module, and it cannot be shared across two loaders:
// v1.4.0's release failed at `createStagingRepository` for exactly that, having passed every check
// CI ran.
apply(plugin = "com.google.devtools.ksp")
