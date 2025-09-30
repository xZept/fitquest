// build.gradle.kts (project level)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.android.library) apply false   // if you use KSP in modules
    // if you use kapt via plugin id, you can declare it here too:
    // id("org.jetbrains.kotlin.kapt") version "1.9.24" apply false
}
// no `apply plugin:` lines here
