plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.grasscutter.m"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.grasscutter.m"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "7.0.0"
    }

    buildFeatures { compose = true; buildConfig = true }
}

// The desktop project remains the source of truth for generated game data.
val upstreamResources = rootProject.file("../Source/GrasscutterTools/Resources")
val syncedResources = layout.buildDirectory.dir("generated/upstreamAssets")
val syncUpstreamResources by tasks.registering(Sync::class) {
    from(upstreamResources) {
        include("zh-cn/*.txt", "zh-tw/*.txt", "en-us/*.txt", "ru-ru/*.txt")
        into("upstream")
    }
    into(syncedResources)
    inputs.dir(upstreamResources)
}

android.sourceSets.getByName("main").assets.srcDir(syncedResources)
tasks.named("preBuild").configure { dependsOn(syncUpstreamResources) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
