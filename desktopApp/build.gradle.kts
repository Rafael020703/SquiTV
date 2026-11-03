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
val installerMode = providers.gradleProperty("desktopInstaller").orElse(if (isDebug) "offline" else "online").get()
require(installerMode in setOf("online", "offline")) { "desktopInstaller must be online or offline" }
val onlineInstaller = installerMode == "online"
val desktopVersion = "1.22.2"
val appName = if (isDebug) "Squi TV Debug" else "Squi TV"
val dataFolder = if (isDebug) "SquiTVDebug" else "SquiTV"
layout.buildDirectory.set(layout.projectDirectory.dir("build/$variant"))
kotlin { jvmToolchain(21) }

// Full library for compilation; only referenced, unmodified vectors enter the installer.
// Keep core icons/builders as their original runtime dependency.
val desktopIconsVersion = "1.7.3"
val desktopIconArchive = configurations.create("desktopIconArchive") { isTransitive = false }
val prepareDesktopIcons = tasks.register<rsv.squitv.build.PrepareDesktopIcons>("prepareDesktopIcons") {
    sourceJar.set(layout.file(providers.provider { desktopIconArchive.singleFile }))
    compiledClasses.from(sourceSets.main.get().output.classesDirs)
    outputJar.set(layout.buildDirectory.file("generated/icons/material-icons-used.jar"))
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation("net.java.dev.jna:jna-platform:5.19.1")
    compileOnly("org.jetbrains.compose.material:material-icons-extended:$desktopIconsVersion")
    add(desktopIconArchive.name, "org.jetbrains.compose.material:material-icons-extended-desktop:$desktopIconsVersion")
    implementation("org.jetbrains.compose.material:material-icons-core:$desktopIconsVersion")
    runtimeOnly(files(prepareDesktopIcons.flatMap { it.outputJar }))
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
kotlin.sourceSets.main { kotlin.srcDir(rootProject.layout.projectDirectory.dir("uiCommon/src/main/kotlin")) }
tasks.named("compileKotlin") { dependsOn(generateBuildInfo) }

val desktopAssets = tasks.register<rsv.squitv.build.GenerateDesktopAssets>("generateDesktopAssets") {
    outputDirectory.set(layout.buildDirectory.dir("generated/assets"))
}
tasks.named<ProcessResources>("processResources") {
    from(desktopAssets.flatMap { it.outputDirectory })
}
val nativeResources = rootProject.layout.buildDirectory.dir("desktopNativeResources/vlc-3.0.23")
val prepareVlc = tasks.register<rsv.squitv.build.PrepareVlcRuntime>("prepareVlcRuntime") {
    definitionFile.set(layout.projectDirectory.file("runtime/vlc.properties"))
    outputDirectory.set(nativeResources)
    archiveFile.set(rootProject.layout.projectDirectory.file(".artifacts/native/vlc-3.0.23-win64.zip"))
    enabled = System.getProperty("os.name").startsWith("Windows")
}
tasks.named<ProcessResources>("processResources") {
    from(prepareVlc.flatMap { it.outputDirectory.dir("windows/vlc") }) {
        include("SQUI-TV-RUNTIME.json")
        into("native-runtime")
    }
}
val onlineResources = tasks.register<Sync>("prepareOnlineResources") {
    from(prepareVlc.flatMap { it.outputDirectory }) { include("windows/vlc/SQUI-TV-RUNTIME.json") }
    into(layout.buildDirectory.dir("online-resources"))
}
tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(if (onlineInstaller) onlineResources else prepareVlc, desktopAssets)
}

compose.desktop {
    application {
        mainClass = "rsv.squitv.desktop.MainKt"
        javaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(if (onlineInstaller) TargetFormat.Msi else TargetFormat.Exe)
            packageName = dataFolder
            packageVersion = desktopVersion
            vendor = "Squi TV"
            description = appName
            appResourcesRootDir.set(if (onlineInstaller) layout.buildDirectory.dir("online-resources") else nativeResources)
            modules("java.desktop", "java.logging", "java.naming", "java.management", "jdk.unsupported", "jdk.crypto.ec")
            windows {
                iconFile.set(desktopAssets.flatMap { it.outputDirectory.file("brand/app-icon.ico") })
                perUserInstall = !onlineInstaller
                if (onlineInstaller) installationPath = appName
                dirChooser = true
                shortcut = true
                menuGroup = appName
                upgradeUuid = if (isDebug) "b473bd19-d856-4902-8aba-8a281b202ac8" else "cf4fe9a9-552b-43ed-8ca3-9e226f623362"
            }
        }
    }
}
val installerFile = layout.buildDirectory.file("compose/binaries/main/exe/$dataFolder-$desktopVersion.exe")
val packageOnlineExe = tasks.register<Exec>("packageOnlineExe") {
    group = "distribution"
    description = "Build the EXE that verifies/installs cached native components before the per-user application MSI."
    dependsOn("packageMsi", prepareVlc, desktopAssets)
    val msi = layout.buildDirectory.file("compose/binaries/main/msi/$dataFolder-$desktopVersion.msi")
    val runtimeManifest = prepareVlc.flatMap { it.outputDirectory.file("windows/vlc/SQUI-TV-RUNTIME.json") }
    val builder = rootProject.layout.projectDirectory.file("tools/build-windows-online-installer.ps1")
    val source = rootProject.layout.projectDirectory.file("tools/windows-installer/Bootstrapper.cs")
    val icon = desktopAssets.flatMap { it.outputDirectory.file("brand/app-icon.ico") }
    inputs.files(msi, runtimeManifest, builder, source, icon,
        rootProject.layout.projectDirectory.file("tools/windows-installer/Maintenance.cs"),
        rootProject.layout.projectDirectory.file("tools/windows-installer/prepare-msi.ps1"))
    inputs.property("version", desktopVersion); inputs.property("variant", variant)
    outputs.file(installerFile)
    outputs.files(layout.buildDirectory.file("compose/binaries/main/exe/installer-definition.json"),
        layout.buildDirectory.file("compose/binaries/main/exe/installer.manifest"),
        layout.buildDirectory.file("compose/binaries/main/exe/payloads/application.msi"),
        layout.buildDirectory.file("compose/binaries/main/exe/payloads/legacy-user.mst"))
    commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", builder.asFile.absolutePath,
        "-Msi", msi.get().asFile.absolutePath, "-RuntimeManifest", runtimeManifest.get().asFile.absolutePath,
        "-Source", source.asFile.absolutePath, "-Icon", icon.get().asFile.absolutePath,
        "-Output", installerFile.get().asFile.absolutePath, "-Variant", variant, "-Version", desktopVersion)
    enabled = onlineInstaller && System.getProperty("os.name").startsWith("Windows")
}
if (onlineInstaller) tasks.register("packageExe") { group = "distribution"; dependsOn(packageOnlineExe) }

tasks.matching { it.name == "run" }.configureEach {
    dependsOn(prepareVlc)
    if (this is JavaExec) systemProperty("squitv.vlc.dir", nativeResources.get().dir("windows/vlc").asFile.absolutePath)
}

tasks.register<rsv.squitv.build.WindowsUpdateMetadata>("writeUpdateMetadata") {
    group = "distribution"
    description = "Package the release and record its actual hash, size, version and runtime architecture."
    dependsOn("createDistributable", if (onlineInstaller) "packageOnlineExe" else "packageExe")
    installer.set(layout.buildDirectory.file("compose/binaries/main/exe/$dataFolder-$desktopVersion.exe"))
    runtimeJvm.set(layout.buildDirectory.file("compose/binaries/main/app/$dataFolder/runtime/bin/server/jvm.dll"))
    version.set(desktopVersion)
    debug.set(isDebug)
    metadataFile.set(layout.buildDirectory.file("update-metadata/windows-release.json"))
    doFirst { require(!debug.get()) { "Use the release variant for production update metadata" } }
}
