import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    id("prevham.kotlin.jvm")
    id("prevham.ktlint")
    id("prevham.publishing")
}

mavenPublishing {
    coordinates(artifactId = "prevham-runtime")
    // Ships the KDoc on @Prev as a real -javadoc.jar instead of the empty placeholder Maven Central
    // would otherwise accept, so the annotation documents itself in consumers' IDEs.
    configure(KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaHtml")))

    pom {
        name.set("PrevHam Runtime")
        description.set("The @Prev annotation - the public API consumers of PrevHam compile against.")
    }
}

dependencies {
    testImplementation(libs.junit)
}
