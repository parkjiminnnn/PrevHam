package prevham.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion

internal fun configureAndroidCommon(extension: CommonExtension) {
    extension.compileSdk = 36
    extension.compileSdkMinor = 1

    extension.defaultConfig.minSdk = 26
    extension.defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    extension.compileOptions.sourceCompatibility = JavaVersion.VERSION_11
    extension.compileOptions.targetCompatibility = JavaVersion.VERSION_11
}
