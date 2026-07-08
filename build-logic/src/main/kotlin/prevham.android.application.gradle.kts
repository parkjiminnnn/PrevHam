import com.android.build.api.dsl.ApplicationExtension
import prevham.buildlogic.configureAndroidCommon
import prevham.buildlogic.configureJvmTarget11

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<ApplicationExtension> {
    configureAndroidCommon()

    defaultConfig {
        targetSdk = 36
    }
}

kotlin {
    compilerOptions {
        configureJvmTarget11()
    }
}
