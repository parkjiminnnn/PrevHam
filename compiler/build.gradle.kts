import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    id("prevham.kotlin.jvm")
    id("prevham.ktlint")
    id("prevham.ksp")
    id("prevham.publishing")
}

mavenPublishing {
    coordinates(artifactId = "prevham-compiler")
    configure(KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaHtml")))

    pom {
        name.set("PrevHam Compiler")
        description.set("The KSP processor that generates Jetpack Compose Preview functions and mock data at compile time.")
    }
}

dependencies {
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinx.serialization.json)
    compileOnly(libs.autoservice.annotations)
    ksp(libs.autoservice.ksp)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.compile.testing.core)
    testImplementation(libs.kotlin.compile.testing.ksp)
}
