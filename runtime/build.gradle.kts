import com.vanniktech.maven.publish.KotlinJvm

plugins {
    id("prevham.kotlin.jvm")
    id("prevham.ktlint")
    id("prevham.publishing")
}

mavenPublishing {
    coordinates(artifactId = "prevham-runtime")
    configure(KotlinJvm())

    pom {
        name.set("PrevHam Runtime")
        description.set("The @Prev annotation - the public API consumers of PrevHam compile against.")
    }
}

dependencies {
    testImplementation(libs.junit)
}
