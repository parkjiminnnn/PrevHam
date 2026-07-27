import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    id("com.vanniktech.maven.publish")
}

// Published coordinates are `io.github.parkjiminnnn:<artifactId>:<version>`. The artifactId is set
// per module (see each module's `mavenPublishing { coordinates(...) }` block); group and version are
// shared here. Override the version at release time with `-PVERSION_NAME=1.0.0`.
group = "io.github.parkjiminnnn"
version = providers.gradleProperty("VERSION_NAME").getOrElse("1.0.0-SNAPSHOT")

extensions.configure<MavenPublishBaseExtension> {
    // Uploads to Maven Central as a deployment that waits for a manual "Publish" on the Central
    // Portal website. Deliberately not automaticRelease: publishing to Maven Central is
    // irreversible, so the deployment is left pending to be inspected — and dropped if something
    // is wrong — before it goes public. Requires `mavenCentralUsername`/`mavenCentralPassword`
    // Gradle properties.
    publishToMavenCentral(automaticRelease = false)

    // Maven Central requires every artifact to be GPG-signed. Signing is only enforced for
    // non-SNAPSHOT versions, so `publishToMavenLocal -PVERSION_NAME=...-SNAPSHOT` works without
    // any signing credentials configured.
    signAllPublications()

    pom {
        inceptionYear.set("2026")
        url.set("https://github.com/parkjiminnnn/PrevHam")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("parkjiminnnn")
                name.set("Jimin Park")
                url.set("https://github.com/parkjiminnnn")
            }
        }

        scm {
            url.set("https://github.com/parkjiminnnn/PrevHam")
            connection.set("scm:git:git://github.com/parkjiminnnn/PrevHam.git")
            developerConnection.set("scm:git:ssh://git@github.com/parkjiminnnn/PrevHam.git")
        }
    }
}
