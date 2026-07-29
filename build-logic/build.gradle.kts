plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.ktlint.gradlePlugin)
    implementation(libs.kotlin.compose.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.mavenPublish.gradlePlugin)
    implementation(libs.dokka.gradlePlugin)
}
