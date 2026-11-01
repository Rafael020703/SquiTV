package rsv.squitv.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val isChild: Boolean = false
)
