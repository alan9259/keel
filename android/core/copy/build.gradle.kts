plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

// The copy tests read the iOS Swift sources (the reference copy) and every shipping
// Android Kotlin source directly, so declare both as inputs: a copy change on either
// platform re-runs them instead of being skipped as up to date.
val repoRoot = rootProject.layout.projectDirectory.dir("..")
tasks.withType<Test>().configureEach {
    systemProperty("keel.repoRoot", repoRoot.asFile.absolutePath)
    inputs.files(fileTree(repoRoot.dir("Keel")) { include("**/*.swift") })
        .withPropertyName("iosSwiftSources").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(
        fileTree(rootProject.layout.projectDirectory) {
            include("**/src/main/**/*.kt", "**/src/release/**/*.kt")
            exclude("**/build/**")
        },
    ).withPropertyName("androidKotlinSources").withPathSensitivity(PathSensitivity.RELATIVE)
}
