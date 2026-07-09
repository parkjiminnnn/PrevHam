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
}
