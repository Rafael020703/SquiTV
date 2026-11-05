package rsv.squitv.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.*
import rsv.squitv.shared.network.AppDns
import rsv.squitv.shared.network.AppDnsMode

/** Device/variant network preference, intentionally separate from content/account backups. */
internal fun initializeDesktopAppDns(directory: Path) {
    val file = directory.resolve("app-dns.json")
    var writable = true
    val mode = try {
        if (!Files.exists(file)) null else {
            require(Files.size(file) in 1..4096)
            val root = Json.parseToJsonElement(Files.readString(file)).jsonObject
            require(root["schema"] == JsonPrimitive(1))
            root.getValue("mode").jsonPrimitive.content.also { value -> require(AppDnsMode.entries.any { it.name == value }) }
        }
    } catch (_: Exception) { writable = false; "SYSTEM" }
    AppDns.configure(mode, persist = { choice ->
        check(writable)
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "app-dns-", ".tmp")
        try {
            Files.writeString(temporary, buildJsonObject { put("schema", 1); put("mode", choice) }.toString())
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
    }, error = if (writable) null else "Não foi possível ler o DNS salvo. O arquivo foi preservado; usando o DNS do aparelho.")
}
