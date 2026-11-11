import groovy.json.JsonSlurper
import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

val noticeBackend = providers.gradleProperty("noticeBackend").getOrElse("emulator")
require(noticeBackend in setOf("demo", "emulator", "live"))
val isLive = noticeBackend == "live"
// The trusted operations service currently supports only disposable emulator data.
val operationsUrl = providers.gradleProperty("operationsUrl").getOrElse(if (isLive) "" else "http://10.0.2.2:8787")
require(!isLive || operationsUrl.isEmpty()) { "Live transaction service is not implemented. Do not point live accounts at a test service." }

val adminLive = isLive && providers.gradleProperty("adminLive").orNull == "true"
val adminUseProjectSigning = adminLive && providers.gradleProperty("adminUseProjectSigning").orNull == "true"
val playerId = "bd.arena.prototype"
val adminId = "$playerId.admin"

fun firebaseConfig(configFile: File, packageId: String): List<String> {
    require(configFile.isFile) { "Missing Firebase configuration for $packageId: ${configFile.name}" }
    val config = JsonSlurper().parse(configFile) as Map<*, *>
    val project = config["project_info"] as Map<*, *>
    val clients = config["client"] as List<*>
    val client = clients.filterIsInstance<Map<*, *>>().firstOrNull {
        val info = it["client_info"] as? Map<*, *>
        (info?.get("android_client_info") as? Map<*, *>)?.get("package_name") == packageId
    } ?: error("Firebase Android package must match $packageId.")
    val info = client["client_info"] as Map<*, *>
    val keys = client["api_key"] as List<*>
    val projectId = project["project_id"] as? String ?: ""
    val appId = info["mobilesdk_app_id"] as? String ?: ""
    val apiKey = keys.filterIsInstance<Map<*, *>>().firstOrNull()?.get("current_key") as? String ?: ""
    val senderId = project["project_number"] as? String ?: ""
    require(projectId.isNotBlank() && !projectId.startsWith("demo-") && appId.isNotBlank() && apiKey.isNotBlank()) {
        "Live Firebase configuration is incomplete for $packageId."
    }
    require(senderId.matches(Regex("[0-9]+"))) { "Firebase project number is missing for $packageId." }
    return listOf(projectId, appId, apiKey, project["storage_bucket"] as? String ?: "", senderId)
}

val localFirebase = listOf("demo-arena", "1:1234567890:android:arena-local", "local-emulator-key", "", "1234567890")
val playerFirebase = if (isLive) firebaseConfig(file("google-services.json"), playerId) else localFirebase
val adminFirebase = if (adminLive) firebaseConfig(file("src/admin/google-services.json"), adminId) else localFirebase
require(!adminLive || adminFirebase.first() == playerFirebase.first()) { "Player and admin must use the same Firebase project." }

android {
    namespace = "bd.arena.prototype"
    compileSdk = 34
    defaultConfig {
        if (!isLive) applicationIdSuffix = if (noticeBackend == "emulator") ".local" else ".demo"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = if(isLive)"1.0.0" else "1.0.0-$noticeBackend"
        buildConfigField("String", "NOTICE_BACKEND", "\"$noticeBackend\"")
        buildConfigField("String", "OPERATIONS_URL", "\"$operationsUrl\"")
        buildConfigField("Boolean", "FIREBASE_USE_EMULATOR", if (isLive) "false" else "true")
        buildConfigField("Boolean", "LOCAL_OPERATIONS", (!isLive).toString())
        manifestPlaceholders["allowEmulatorTraffic"] = (!isLive).toString()
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    if (isLive && (!adminLive || adminUseProjectSigning)) {
        val signingFile = rootProject.file("../.signing/release.properties")
        require(signingFile.isFile) { "Production signing configuration is missing." }
        val signing = Properties().apply { signingFile.inputStream().use { load(it) } }
        signingConfigs.create("production") {
            storeFile = rootProject.file("../.signing/antihack-release.jks")
            storePassword = signing.getProperty("storePassword")
            keyAlias = "antihack"
            keyPassword = signing.getProperty("storePassword")
        }
    }
    if (adminLive && !adminUseProjectSigning) {
        val signingFile = rootProject.file("../.signing/admin-release.properties")
        require(signingFile.isFile) { "Admin signing configuration is missing." }
        val signing = Properties().apply { signingFile.inputStream().use { load(it) } }
        fun signingValue(name: String): String = requireNotNull(signing.getProperty(name)?.takeIf { it.isNotBlank() }) {
            "Admin signing configuration is missing $name."
        }
        signingConfigs.create("adminProduction") {
            storeFile = signingFile.parentFile.resolve(signingValue("storeFile"))
            storePassword = signingValue("storePassword")
            keyAlias = signingValue("keyAlias")
            keyPassword = signingValue("keyPassword")
        }
    }
    flavorDimensions += "audience"
    productFlavors {
        create("player") {
            dimension = "audience"
            applicationId = playerId
            if (isLive) {
                versionCode = 23
                versionName = "1.0.11"
            }
            manifestPlaceholders["appLabel"] = if (isLive) "Antihack Tour" else "Antihack Tour Local"
            manifestPlaceholders["launcherActivity"] = ".MainActivity"
            if (isLive && !adminLive) signingConfig = signingConfigs.getByName("production")
        }
        create("admin") {
            dimension = "audience"
            applicationId = adminId
            if (isLive) {
                versionCode = 27
                versionName = "1.0.15"
            }
            manifestPlaceholders["appLabel"] = if (isLive) "Antihack Admin" else "Antihack Admin Local"
            manifestPlaceholders["launcherActivity"] = ".AdminActivity"
            if (adminLive) signingConfig = signingConfigs.getByName(if (adminUseProjectSigning) "production" else "adminProduction")
        }
        forEach { flavor ->
            val config = if (flavor.name == "admin") adminFirebase else playerFirebase
            listOf("PROJECT_ID", "APP_ID", "API_KEY", "STORAGE_BUCKET", "MESSAGING_SENDER_ID").zip(config).forEach { (name, value) ->
                flavor.buildConfigField("String", "FIREBASE_$name", "\"$value\"")
            }
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

androidComponents {
    beforeVariants(selector().withFlavor("audience" to "player")) {
        it.enable = !adminLive
    }
    beforeVariants(selector().withFlavor("audience" to "admin")) {
        it.enable = noticeBackend == "emulator" || adminLive
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.firebase:firebase-firestore:25.1.4")
    implementation("com.google.firebase:firebase-auth:23.0.0")
    "playerImplementation"("com.google.firebase:firebase-messaging:24.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.14.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}

tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}
