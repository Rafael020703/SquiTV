package rsv.squitv.data.local.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * Cache and queue status table for VOD/Series metadata preparation.
 */
@Entity(
    tableName = "catalog_metadata_cache",
    primaryKeys = ["accountKey", "streamType", "streamId"],
    indices = [
        Index(value = ["accountKey", "streamType", "status"]),
        Index(value = ["nextEligibleAt"])
    ]
)
data class CatalogMetadataEntity(
    val accountKey: String,
    val streamType: String, // VOD or SERIES
    val streamId: Int,
    val status: String, // PENDING, IN_PROGRESS, SUCCESS, NO_DATA, FAILED_TEMPORARY
    val genre: String? = null,
    val lastAttempt: Long = 0L,
    val lastSuccess: Long = 0L,
    val nextEligibleAt: Long = 0L,
    val attempts: Int = 0,
    val errorMessage: String? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_NO_DATA = "NO_DATA"
        const val STATUS_FAILED_TEMPORARY = "FAILED_TEMPORARY"
    }
}
