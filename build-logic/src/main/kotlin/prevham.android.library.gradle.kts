import com.android.build.api.dsl.LibraryExtension
import prevham.buildlogic.configureAndroidCommon
import prevham.buildlogic.configureJvmTarget11

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

extensions.configure<LibraryExtension> {
    configureAndroidCommon()
}

kotlin {
    compilerOptions {
        configureJvmTarget11()
    }
}
