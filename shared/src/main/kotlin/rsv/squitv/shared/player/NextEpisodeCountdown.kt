package rsv.squitv.shared.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Main-thread controller. Both cancellation and immediate playback consume the pending action. */
class NextEpisodeCountdown(private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow<Int?>(null)
    val seconds = mutable.asStateFlow()
    private var job: Job? = null
    private var completion: (() -> Unit)? = null
    private var revision = 0L
    fun start(onFinished: () -> Unit) {
        cancel()
        completion = onFinished
        val token = revision
        job = scope.launch {
            for (value in 5 downTo 1) {
                if (token != revision) return@launch
                mutable.value = value
                delay(1000)
            }
            if (token == revision) finish()
        }
    }
    fun cancel() { revision++; job?.cancel(); job = null; completion = null; mutable.value = null }
    fun finish() { val action = completion ?: return; cancel(); action() }
}
