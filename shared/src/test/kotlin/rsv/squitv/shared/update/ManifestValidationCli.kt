package rsv.squitv.shared.update

import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.serialization.json.Json

/** Verification entry point; deliberately excluded from the library shipped with Android. */
object ManifestValidationCli {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Pass exactly one manifest path" }
        val path = Paths.get(args.single())
        require(Files.size(path) in 1..262_144) { "Manifest size outside supported bounds" }
        val json = Json { ignoreUnknownKeys = true }
        val text = Files.readString(path)
        val manifest = json.decodeFromString<UpdateManifest>(text)
        require(UpdateManifestValidator.validate(manifest)) { "Invalid update manifest" }
        if (manifest.changelogs.isNotEmpty()) {
            val platforms = manifest.artifacts.map { it.platform }.toSet()
            require(manifest.changelogs.keys == platforms && manifest.changelogs.values.all { it.isNotBlank() }) {
                "New releases require a non-empty changelog for each included platform"
            }
            for (platform in platforms) {
                require(PlatformUpdateNotes.select(manifest, platform, "legacy") == manifest.changelogs[platform])
                val referencePath = path.parent.parent.resolve("channel-index/$platform.json")
                require(Files.size(referencePath) in 1..4096) { "Missing or oversized platform reference" }
                val reference = json.decodeFromString<UpdateChannelReference>(Files.readString(referencePath))
                require(reference.validate(platform) && reference.matches(text, manifest)) { "Platform reference does not match final manifest" }
            }
        }
        println("Valid schema-v1 manifest: " + manifest.releaseTag + " / " + manifest.artifacts.size + " artifact(s)")
    }
}
