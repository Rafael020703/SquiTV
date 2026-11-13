package rsv.squitv.data.local.entities

import androidx.room.Entity

@Entity(tableName = "downloads", primaryKeys = ["streamId", "type"])
data class DownloadEntity(
    val streamId: Int,
    val name: String,
    val type: String, // movie or series
    val icon: String?,
    val localUri: String,
    val status: String, // PENDING, DOWNLOADING, COMPLETED, FAILED
    val progress: Float = 0f,
    val size: Long = 0L,
    val downloadedBytes: Long = 0L,
    val duration: String? = null,
    val container: String? = "ts",
    val seriesId: Int? = null,
    val seasonNumber: Int? = null,
    val downloadSpeedMbps: Double = 0.0
)
