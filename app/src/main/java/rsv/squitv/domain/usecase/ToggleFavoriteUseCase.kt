package rsv.squitv.domain.usecase

import rsv.squitv.core.data.repository.UserRepository
import rsv.squitv.domain.model.IptvItem
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(private val userRepository: UserRepository) {
    suspend operator fun invoke(item: IptvItem, isFav: Boolean, profileId: String? = null) {
        userRepository.updateFavorite(item, isFav, profileId)
    }
}
