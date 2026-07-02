import com.android.build.api.dsl.LibraryExtension
import prevham.buildlogic.configureAndroidCommon

plugins {
    id("com.android.library")
}

extensions.configure<LibraryExtension> {
    configureAndroidCommon()
}
