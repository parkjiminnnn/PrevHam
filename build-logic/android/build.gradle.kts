plugins {
    `java-gradle-plugin`
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

dependencies {
    implementation(libs.android.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "prevham.android.application"
            implementationClass = "prevham.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "prevham.android.library"
            implementationClass = "prevham.buildlogic.AndroidLibraryConventionPlugin"
        }
    }
}