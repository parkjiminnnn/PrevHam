package prevham.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion

internal fun CommonExtension.configureAndroidCommon() {
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig.minSdk = 26
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    compileOptions.sourceCompatibility = JavaVersion.VERSION_11
    compileOptions.targetCompatibility = JavaVersion.VERSION_11
}
