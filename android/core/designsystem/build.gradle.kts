plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.therecalibrationyears.keel.designsystem"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    api(project(":core:domain"))
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Fonts are single-sourced from the iOS app (Keel/Resources/Fonts): copied into a
// generated res/font at build time with Android-legal names, never duplicated in git.
abstract class CopyKeelFonts : DefaultTask() {
    @get:InputDirectory abstract val source: DirectoryProperty
    @get:OutputDirectory abstract val output: DirectoryProperty

    @TaskAction fun copy() {
        val fontDir = output.get().dir("font").asFile.apply { deleteRecursively(); mkdirs() }
        val names = mapOf(
            "Literata.ttf" to "literata.ttf",
            "Poppins-Light.ttf" to "poppins_light.ttf",
            "Poppins-Regular.ttf" to "poppins_regular.ttf",
            "Poppins-Medium.ttf" to "poppins_medium.ttf",
        )
        names.forEach { (from, to) ->
            source.get().file(from).asFile.copyTo(fontDir.resolve(to), overwrite = true)
        }
    }
}

val copyKeelFonts = tasks.register<CopyKeelFonts>("copyKeelFonts") {
    source.set(rootProject.layout.projectDirectory.dir("../Keel/Resources/Fonts"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(copyKeelFonts, CopyKeelFonts::output)
    }
}

// Tests read files outside this module (iOS mood emoji, brand art, launcher icon), so a
// change to any of them re-runs them.
val repoRoot = rootProject.layout.projectDirectory.dir("..")
tasks.withType<Test>().configureEach {
    systemProperty("keel.repoRoot", repoRoot.asFile.absolutePath)
    inputs.files(
        repoRoot.file("Keel/Models/Mood.swift"), repoRoot.file("Keel/Models/MoodPack.swift"),
        repoRoot.file("brand/keel-icon-1024.png"),
        repoRoot.file("android/app/src/main/res/drawable/ic_launcher_foreground.xml"),
        repoRoot.dir("Keel/Resources/Assets.xcassets/SocialInstagram.imageset"),
        repoRoot.dir("Keel/Resources/Assets.xcassets/SocialFacebook.imageset"),
    ).withPropertyName("repoSources").withPathSensitivity(PathSensitivity.RELATIVE)
    // ThemeColourTests scans every module's shipping Kotlin for raw colours.
    inputs.files(
        fileTree(rootProject.layout.projectDirectory) {
            include("**/src/main/**/*.kt")
            exclude("**/build/**")
        },
    ).withPropertyName("shippingKotlin").withPathSensitivity(PathSensitivity.RELATIVE)
}
