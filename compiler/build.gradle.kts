plugins {
    id("prevham.kotlin.jvm")
    id("prevham.ktlint")
    id("prevham.ksp")
}

dependencies {
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlinpoet)
    compileOnly(libs.autoservice.annotations)
    ksp(libs.autoservice.ksp)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.compile.testing.core)
    testImplementation(libs.kotlin.compile.testing.ksp)
}
