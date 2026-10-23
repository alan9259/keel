plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.therecalibrationyears.keel.data"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Room schema JSON is committed so every later change is a reviewed migration (plan D6).
room { schemaDirectory("$projectDir/schemas") }

dependencies {
    api(project(":core:domain"))
    api(libs.room.runtime)
    ksp(libs.room.compiler)
    api(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}

// The treatment picker list is single-sourced from the iOS app
// (Keel/Resources/treatment-catalog.json): copied into generated assets at build time,
// never duplicated in git.
abstract class CopyTreatmentCatalog : DefaultTask() {
    @get:InputFile abstract val source: RegularFileProperty
    @get:OutputDirectory abstract val output: DirectoryProperty

    @TaskAction fun copy() {
        val dir = output.get().asFile.apply { deleteRecursively(); mkdirs() }
        source.get().asFile.copyTo(dir.resolve("treatment-catalog.json"), overwrite = true)
    }
}

val copyTreatmentCatalog = tasks.register<CopyTreatmentCatalog>("copyTreatmentCatalog") {
    source.set(rootProject.layout.projectDirectory.file("../Keel/Resources/treatment-catalog.json"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyTreatmentCatalog, CopyTreatmentCatalog::output)
    }
}

android { testOptions { unitTests { isIncludeAndroidResources = true } } }

// Tests compare the bundled catalog with the iOS file.
tasks.withType<Test>().configureEach {
    val repoRoot = rootProject.layout.projectDirectory.dir("..")
    systemProperty("keel.repoRoot", repoRoot.asFile.absolutePath)
    inputs.file(repoRoot.file("Keel/Resources/treatment-catalog.json")).withPropertyName("iosCatalog").withPathSensitivity(PathSensitivity.RELATIVE)
}

// The backup tests read the shared fixtures (repo-root fixtures/, written by Swift's encoder from
// the iOS backup structs; see tools/ios-backup), so a change there re-runs them.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.layout.projectDirectory.dir("../fixtures")).withPropertyName("sharedFixtures")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
