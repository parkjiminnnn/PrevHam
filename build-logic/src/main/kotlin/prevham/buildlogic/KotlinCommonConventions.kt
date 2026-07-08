package prevham.buildlogic

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

internal fun KotlinJvmCompilerOptions.configureJvmTarget11() {
    jvmTarget.set(JvmTarget.JVM_11)
}
