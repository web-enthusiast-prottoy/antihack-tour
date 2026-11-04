import groovy.json.JsonSlurper
import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

val noticeBackend = providers.gradleProperty("noticeBackend").getOrElse("emulator")
require(noticeBackend in setOf("demo", "emulator", "live"))
val isLive = noticeBackend == "live"
// The trusted operations service currently supports only disposable emulator data.
val operationsUrl = providers.gradleProperty("operationsUrl").getOrElse(if (isLive) "" else "http://10.0.2.2:8787")
require(!isLive || operationsUrl.isEmpty()) { "Live transaction service is not implemented. Do not point live accounts at a test service." }

var firebaseProjectId = "demo-arena"
var firebaseAppId = "1:1234567890:android:arena-local"
var firebaseApiKey = "local-emulator-key"
var firebaseStorageBucket = ""
if (isLive) {
    val googleServicesFile = file("google-services.json")
    require(googleServicesFile.isFile) { "A live build requires app/google-services.json." }
    val config = JsonSlurper().parse(googleServicesFile) as Map<*, *>
    val project = config["project_info"] as? Map<*, *> ?: error("Missing Firebase project information.")
    val clients = config["client"] as? List<*> ?: error("Missing Firebase Android client.")
    val client = clients.filterIsInstance<Map<*, *>>().firstOrNull {
        val info = it["client_info"] as? Map<*, *>
        val android = info?.get("android_client_info") as? Map<*, *>
        android?.get("package_name") == "bd.arena.prototype"
    } ?: error("Firebase Android package must match bd.arena.prototype.")
    val info = client["client_info"] as Map<*, *>
    val keys = client["api_key"] as? List<*>
    firebaseProjectId = project["project_id"] as? String ?: error("Missing Firebase project ID.")
    firebaseAppId = info["mobilesdk_app_id"] as? String ?: error("Missing Firebase app ID.")
    firebaseApiKey = keys?.filterIsInstance<Map<*, *>>()?.firstOrNull()?.get("current_key") as? String ?: error("Missing Firebase API key.")
    firebaseStorageBucket = project["storage_bucket"] as? String ?: ""
    require(firebaseProjectId.isNotBlank() && !firebaseProjectId.startsWith("demo-") && firebaseAppId.isNotBlank() && firebaseApiKey.isNotBlank()) { "Live Firebase configuration is incomplete." }
}

android {
    namespace = "bd.arena.prototype"
    compileSdk = 34
    defaultConfig {
        applicationId = "bd.arena.prototype"
        if (!isLive) applicationIdSuffix = if (noticeBackend == "emulator") ".local" else ".demo"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = if(isLive)"1.0.0" else "1.0.0-$noticeBackend"
        buildConfigField("String", "NOTICE_BACKEND", "\"$noticeBackend\"")
        buildConfigField("String", "OPERATIONS_URL", "\"$operationsUrl\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$firebaseAppId\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"$firebaseStorageBucket\"")
        buildConfigField("Boolean", "FIREBASE_USE_EMULATOR", if (isLive) "false" else "true")
        buildConfigField("Boolean", "LOCAL_OPERATIONS", (!isLive).toString())
        manifestPlaceholders["allowEmulatorTraffic"] = (!isLive).toString()
        manifestPlaceholders["appLabel"] = if (isLive) "Antihack Tour" else "Antihack Tour Local"
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    if(isLive){
        val signingFile=rootProject.file("../.signing/release.properties")
        require(signingFile.isFile){"Production signing configuration is missing."}
        val productionSigning=Properties().apply{signingFile.inputStream().use{load(it)}}
        signingConfigs.create("production"){
            storeFile=rootProject.file("../.signing/antihack-release.jks")
            storePassword=productionSigning.getProperty("storePassword")
            keyAlias="antihack"
            keyPassword=productionSigning.getProperty("storePassword")
        }
    }
    buildTypes { release { isDebuggable=false;isMinifyEnabled = true;if(isLive)signingConfig=signingConfigs.getByName("production");proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.firebase:firebase-firestore:25.1.4")
    implementation("com.google.firebase:firebase-auth:23.0.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}
