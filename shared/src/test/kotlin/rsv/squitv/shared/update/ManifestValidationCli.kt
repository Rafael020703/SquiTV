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
        val manifest = Json { ignoreUnknownKeys = true }.decodeFromString<UpdateManifest>(Files.readString(path))
        require(UpdateManifestValidator.validate(manifest)) { "Invalid update manifest" }
        println("Valid schema-v1 manifest: " + manifest.releaseTag + " / " + manifest.artifacts.size + " artifact(s)")
    }
}
