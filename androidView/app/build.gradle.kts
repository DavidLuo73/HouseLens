import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}
val local = Properties().apply {
    rootProject.file("supabase.local.properties").inputStream().use { load(it) }
}
val projectUrl = local.getProperty("supabase.url") ?: error("Missing SUPABASE_URL")
val publishableKey = local.getProperty("supabase.publishableKey") ?: error("Missing SUPABASE_PUBLISHABLE_KEY")
val projectUri = URI(projectUrl)
require(projectUri.scheme == "https" && projectUri.host != null && projectUri.rawUserInfo == null &&
    projectUri.rawQuery == null && projectUri.rawFragment == null && projectUri.path in listOf("", "/")) {
    "SUPABASE_URL must be an HTTPS project origin"
}
require(publishableKey.startsWith("sb_publishable_")) { "Use a Supabase publishable key" }
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
android {
    namespace = "tw.houselens.viewer"
    compileSdk = 36
    defaultConfig {
        applicationId = "tw.houselens.viewer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", quoted(projectUrl.trimEnd('/')))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", quoted(publishableKey))
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation.compose)
    implementation(libs.browser)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.datastore)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.ktor.mock)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    debugImplementation(libs.compose.test.manifest)
}
