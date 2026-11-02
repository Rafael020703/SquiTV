plugins {
    id("java-library")
    kotlin("jvm")
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.register<JavaExec>("validateUpdateManifest") {
    group = "verification"
    description = "Validate a generated release manifest using the client contract (-Pmanifest=...)."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    workingDir(rootProject.layout.projectDirectory.asFile)
    mainClass.set("rsv.squitv.shared.update.ManifestValidationCli")
    val manifestPath = providers.gradleProperty("manifest")
    doFirst { setArgs(listOf(manifestPath.get())) }
}
