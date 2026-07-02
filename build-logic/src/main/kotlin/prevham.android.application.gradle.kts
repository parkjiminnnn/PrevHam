import com.android.build.api.dsl.ApplicationExtension
import prevham.buildlogic.configureAndroidCommon

plugins {
    id("com.android.application")
}

extensions.configure<ApplicationExtension> {
    configureAndroidCommon()

    defaultConfig {
        targetSdk = 36
    }
}
