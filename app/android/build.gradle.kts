plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}

// External drives can add metadata files that Android resource tools reject.
providers.gradleProperty("buildRoot").orNull?.let { directory ->
    layout.buildDirectory.set(file("$directory/root"))
    subprojects { layout.buildDirectory.set(file("$directory/$name")) }
}
