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

// IncrementalSlotManifestTest runs this processor in real Gradle builds, because incremental
// processing only happens there - kctfork compiles everything every time. It has to be the processor
// as built here rather than a released one, since what those builds check is the processor's own
// behaviour (issue #139). Handed over as paths, and declared as inputs so a change to either reruns
// the tests.
val testConsumerRuntime: Configuration by configurations.creating { isTransitive = false }

dependencies {
    testImplementation(gradleTestKit())
    testConsumerRuntime(project(":runtime"))
}

tasks.test {
    val processorClasspath = files(tasks.jar, configurations.runtimeClasspath)
    val runtimeClasspath = files(testConsumerRuntime)
    val kotlinVersion = libs.versions.kotlin.get()
    val kspVersion = libs.versions.ksp.get()
    inputs.files(processorClasspath).withNormalizer(ClasspathNormalizer::class).withPropertyName("processorClasspath")
    inputs.files(runtimeClasspath).withNormalizer(ClasspathNormalizer::class).withPropertyName("consumerRuntime")
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Dprevham.test.processorClasspath=${processorClasspath.asPath}",
                "-Dprevham.test.runtimeClasspath=${runtimeClasspath.asPath}",
                "-Dprevham.test.kotlinVersion=$kotlinVersion",
                "-Dprevham.test.kspVersion=$kspVersion",
            )
        },
    )
}
