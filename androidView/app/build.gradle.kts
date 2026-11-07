import java.util.Properties
import java.net.URI
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}
fun properties(name: String) = Properties().apply {
    rootProject.file(name).takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val local = properties("supabase.local.properties")
val signing = properties("signing.local.properties")
fun setting(env: String, name: String, values: Properties = local): String? =
    (System.getenv(env) ?: values.getProperty(name))?.takeIf { it.isNotBlank() }
val projectUrl = setting("SUPABASE_URL", "supabase.url") ?: error("Missing SUPABASE_URL")
val publishableKey = setting("SUPABASE_PUBLISHABLE_KEY", "supabase.publishableKey") ?: error("Missing SUPABASE_PUBLISHABLE_KEY")
val projectUri = URI(projectUrl)
require(projectUri.scheme == "https" && projectUri.host != null && projectUri.rawUserInfo == null &&
    projectUri.rawQuery == null && projectUri.rawFragment == null && projectUri.path in listOf("", "/")) {
    "SUPABASE_URL must be an HTTPS project origin"
}
require(publishableKey.matches(Regex("sb_publishable_[A-Za-z0-9_-]+"))) { "Use a Supabase publishable key" }
val storePath = setting("VIEWER_SIGNING_STORE_FILE", "signing.storeFile", signing)
val storeSecret = setting("VIEWER_SIGNING_STORE_PASSWORD", "signing.storePassword", signing)
val signingAlias = setting("VIEWER_SIGNING_KEY_ALIAS", "signing.keyAlias", signing)
val keySecret = setting("VIEWER_SIGNING_KEY_PASSWORD", "signing.keyPassword", signing)
val signingInputs = listOf(storePath, storeSecret, signingAlias, keySecret).all { it != null }
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val releaseGuard = tasks.register("validateReleaseConfiguration") {
    doLast {
        require(signingInputs) { "Release requires all VIEWER_SIGNING_* inputs (or signing.local.properties)" }
        require(signingAlias != "androiddebugkey") { "Release must not use the Android debug identity" }
        val store = rootProject.file(storePath!!)
        require(store.isFile) { "Release signing keystore does not exist" }
        try {
            val keys = KeyStore.getInstance(store, storeSecret!!.toCharArray())
            require(keys.getKey(signingAlias, keySecret!!.toCharArray()) is PrivateKey)
            val certificate = keys.getCertificate(signingAlias) as X509Certificate
            require(!certificate.subjectX500Principal.name.contains("CN=Android Debug", ignoreCase = true))
        } catch (_: Exception) { error("Release signing identity cannot be loaded or is a debug identity") }
    }
}
tasks.configureEach {
    if (name == "preReleaseBuild" || name == "validateSigningRelease") dependsOn(releaseGuard)
}
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
    signingConfigs {
        if (signingInputs) create("release") {
            storeFile = rootProject.file(storePath!!)
            storePassword = storeSecret
            keyAlias = signingAlias
            keyPassword = keySecret
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            if (signingInputs) signingConfig = signingConfigs.getByName("release")
        }
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
    androidTestImplementation(libs.ktor.mock)
    androidTestImplementation(libs.androidx.test.runner)
    // Compose's transitive Espresso 3.5 reflects an API removed in Android 17.
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.test.junit)
    debugImplementation(libs.compose.test.manifest)
}
