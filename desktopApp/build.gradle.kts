import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    id("org.jetbrains.compose") version "1.12.2"
}

val variant = providers.gradleProperty("desktopVariant").orElse("release").get()
require(variant in setOf("debug", "release")) { "desktopVariant must be debug or release" }
val isDebug = variant == "debug"
val desktopVersion = "1.21.0"
val appName = if (isDebug) "Squi TV Debug" else "Squi TV"
val dataFolder = if (isDebug) "SquiTVDebug" else "SquiTV"
layout.buildDirectory.set(layout.projectDirectory.dir("build/$variant"))
kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation("net.java.dev.jna:jna-platform:5.19.1")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.1")
}

val generatedSources = layout.buildDirectory.dir("generated/buildInfo")
val generateBuildInfo = tasks.register("generateBuildInfo") {
    inputs.property("variant", variant)
    inputs.property("version", desktopVersion)
    outputs.dir(generatedSources)
    val outputFile = generatedSources.get().file("rsv/squitv/desktop/DesktopBuildInfo.kt").asFile
    val sourceText = """
            package rsv.squitv.desktop
            internal object DesktopBuildInfo {
                const val VERSION = "$desktopVersion"
                const val DEBUG = $isDebug
                const val APP_NAME = "$appName"
                const val DATA_FOLDER = "$dataFolder"
            }
        """.trimIndent()
    doLast {
        outputFile.parentFile.mkdirs()
        outputFile.writeText(sourceText)
    }
}
kotlin.sourceSets.main { kotlin.srcDir(generatedSources) }
tasks.named("compileKotlin") { dependsOn(generateBuildInfo) }

compose.desktop {
    application {
        mainClass = "rsv.squitv.desktop.MainKt"
        javaHome = System.getenv("JAVA_HOME")
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = dataFolder
            packageVersion = desktopVersion
            vendor = "Squi TV"
            description = appName
            modules("java.desktop", "java.logging", "java.naming", "java.management", "jdk.unsupported", "jdk.crypto.ec")
            windows {
                perUserInstall = true
                dirChooser = true
                shortcut = true
                menuGroup = appName
                upgradeUuid = if (isDebug) "b473bd19-d856-4902-8aba-8a281b202ac8" else "cf4fe9a9-552b-43ed-8ca3-9e226f623362"
            }
        }
    }
}
