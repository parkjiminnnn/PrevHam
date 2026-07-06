plugins {
    id("prevham.android.library")
    id("prevham.ktlint")
}

android {
    namespace = "io.github.parkjiminnnn.runtime"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    testImplementation(libs.junit)
}
