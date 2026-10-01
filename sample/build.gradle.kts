plugins {
    id("prevham.android.application")
    id("prevham.ktlint")
    id("prevham.ksp")
}

android {
    namespace = "io.github.parkjiminnnn.prevham"

    defaultConfig {
        applicationId = "io.github.parkjiminnnn.prevham"
        versionCode = 1
        versionName = "1.0"
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
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }
}

dependencies {
    implementation(project(":runtime"))
    ksp(project(":compiler"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.mockk)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Writes the list of slots a value could be supplied for, so the generation task can ask about the
// ones that have none. A build output, not a source of truth - the committed file is the value file.
ksp {
    arg("prevham.slotManifest", "${layout.buildDirectory.get()}/generated/prevham/mock-value-slots.json")
}

// A committed value file, so the sample shows what a configured value does to a Preview. Only the
// state-holder slots are filled: the point is the shape, not a complete set of values.
//
// The missing-value warning is off for that reason. It exists to tell a project its file has fallen
// behind its code, and here the gaps are deliberate - a build that always warns teaches people to
// stop reading warnings.
ksp {
    arg("prevham.mockValues", "$projectDir/src/main/prevham/mock-values.json")
    arg("prevham.warnOnMissingValues", "false")
}
