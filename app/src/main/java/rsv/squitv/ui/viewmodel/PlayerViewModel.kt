package rsv.squitv.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.StreamRepository
import rsv.squitv.core.domain.interactor.PlaybackManager
import rsv.squitv.core.domain.interactor.PlaybackWatchdog
import rsv.squitv.data.model.EpgProgramme
import rsv.squitv.data.model.XtreamStream
import rsv.squitv.R
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.domain.usecase.GetPlayerEpgUseCase
import rsv.squitv.domain.usecase.GetQuickSwitchUseCase
import rsv.squitv.domain.usecase.PerformDnsFailoverUseCase
import rsv.squitv.domain.usecase.PreparePlaybackSessionUseCase
import rsv.squitv.domain.usecase.SmartDownloadUseCase
import rsv.squitv.domain.usecase.SyncWatchProgressUseCase
import rsv.squitv.domain.usecase.ToggleFavoriteUseCase
import rsv.squitv.appfunctions.AppFunctionActionBus
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.core.debug.*
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import kotlin.time.Duration.Companion.seconds

sealed class PlayerUiState(
    open val position: Long = 0L,
    open val duration: Long = 0L,
    open val name: String? = null
) {
    data class Loading(override val name: String? = null) : PlayerUiState(name = name)
    data class Playing(
        override val position: Long = 0L,
        override val duration: Long = 0L,
        override val name: String? = null,
        val streamIcon: String? = null,
        val isPlaying: Boolean = true,
        val nextEpisodeStreamId: Int? = null,
        val nextEpisodeName: String? = null,
        val contentType: ContentType = ContentType.LIVE, 
        val isLive: Boolean = false,
        val isReconnecting: Boolean = false,
        val savedPosition: Long? = null,
        val tracks: Tracks? = null,
        val isFavorite: Boolean = false,
        val bandwidthMbps: Double = 0.0,
        val bufferDelaySeconds: Long = 0,
        val bufferedPosition: Long = 0L,
        val availableQualities: Map<String, Int> = emptyMap(),
        val epgId: String? = null,
        val streamId: Int? = null,
        val resolution: String? = null,
        val frameRate: Float? = null,
        val videoCodec: String? = null,
        val audioCodec: String? = null,
        val epgListings: List<rsv.squitv.data.model.EpgListing>? = null,
        val signalHealth: Float = 1.0f, // 0.0 to 1.0
        val playbackSpeed: Float = 1.0f
    ) : PlayerUiState(position, duration)
    data class Error(
        val message: String,
        override val position: Long = 0L,
        override val duration: Long = 0L
    ) : PlayerUiState(position, duration)
}

@HiltViewModel
@OptIn(UnstableApi::class)
class PlayerViewModel @Inject constructor(
    private val application: Application,
    private val catalogRepository: CatalogRepository,
    private val streamRepository: StreamRepository,
    private val settingsRepository: SettingsRepository,
    private val syncWatchProgressUseCase: SyncWatchProgressUseCase,
    private val getPlayerEpgUseCase: GetPlayerEpgUseCase,
    private val getQuickSwitchUseCase: GetQuickSwitchUseCase,
    private val performDnsFailoverUseCase: PerformDnsFailoverUseCase,
    private val preparePlaybackSessionUseCase: PreparePlaybackSessionUseCase,
    private val smartDownloadUseCase: SmartDownloadUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val actionBus: AppFunctionActionBus,
    private val playbackWatchdog: PlaybackWatchdog,
    private val playbackManager: PlaybackManager,
    private val bandwidthMeter: DefaultBandwidthMeter
) : ViewModel() {

    val currentPlayer: StateFlow<Player?> = playbackManager.playerState

    private val _zappingSessionId = MutableStateFlow(0)
    val zappingSessionId: StateFlow<Int> = _zappingSessionId.asStateFlow()

    private var currentChannelSwitchId: String? = null

    private val _zappingChannel = MutableStateFlow<XtreamStream?>(null)
    val zappingChannel: StateFlow<XtreamStream?> = _zappingChannel.asStateFlow()

    private var zappingTimerJob: Job? = null
    private var lastLoadedCategoryId: String? = null
    private var epgJob: Job? = null

    private fun showZappingBanner(stream: XtreamStream) {
        _zappingChannel.value = stream
        zappingTimerJob?.cancel()
        zappingTimerJob = viewModelScope.launch {
            delay(5000L)
            _zappingChannel.value = null
        }
    }

    fun dismissZappingBanner() {
        zappingTimerJob?.cancel()
        _zappingChannel.value = null
    }

    init {
        initializeController()
        
        viewModelScope.launch {
            actionBus.actions.collect { action ->
                when (action) {
                    is AppFunctionActionBus.Action.Pause -> {
                        playbackManager.pause()
                    }
                    is AppFunctionActionBus.Action.Resume -> {
                        playbackManager.play()
                    }
                    is AppFunctionActionBus.Action.Stop -> {
                        stopPlayback()
                    }
                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            playbackManager.events.collect { event ->
                when (event) {
                    is PlaybackManager.Event.NextChannelRequested -> {
                        playNextChannel()
                    }
                    is PlaybackManager.Event.PreviousChannelRequested -> {
                        playPreviousChannel()
                    }
                    else -> handlePlaybackEvent(event)
                }
            }
        }
    }

    private fun initializeController() {
        playbackManager.connect { 
            // Controller initialized and listener already registered in PlaybackManager
        }
    }

    private suspend fun ensurePlayer(): Player {
        if (playbackManager.player == null) {
            initializeController()
        }
        var attempts = 0
        while (playbackManager.player == null) {
            delay(100)
            attempts++
            if (attempts > 50) {
                playbackManager.release()
                throw IllegalStateException("Timeout ao conectar com o serviço de reprodução")
            }
        }
        return playbackManager.player!!
    }

    private fun isPlaybackEventValid(sessionId: Int, eventMediaId: String? = null, eventChannelSwitchId: String? = null): Boolean {
        val activeSession = _zappingSessionId.value
        if (sessionId != activeSession) {
            Timber.w("[STALE_CALLBACK_IGNORED] session=$sessionId != activeSession=$activeSession, reason=SESSION_MISMATCH")
            return false
        }
        if (!eventChannelSwitchId.isNullOrBlank() && currentChannelSwitchId != null && eventChannelSwitchId != currentChannelSwitchId) {
            Timber.w("[STALE_CALLBACK_IGNORED] channelSwitchId=$eventChannelSwitchId != currentChannelSwitchId=$currentChannelSwitchId, reason=NEWER_CHANNEL_SWITCH")
            return false
        }
        val expectedMediaId = currentStreamId?.toString()
        if (expectedMediaId != null && eventMediaId != null && eventMediaId != expectedMediaId) {
            Timber.w("[STALE_CALLBACK_IGNORED] eventMediaId=$eventMediaId != expectedMediaId=$expectedMediaId, reason=MEDIA_ID_MISMATCH")
            return false
        }
        return true
    }

    private fun updatePlayingState(p: Player, sessionId: Int = _zappingSessionId.value) {
        val playerMediaId = p.currentMediaItem?.mediaId
        if (!isPlaybackEventValid(sessionId, playerMediaId)) return
        val isLive = currentType == ContentType.LIVE
        val currentState = _uiState.value
        val resolvedIcon = currentStreamIcon
        Timber.d("[ARTWORK][session=$sessionId][stream=$currentStreamId] updatePlayingState -> resolvedIcon=$resolvedIcon")
        
        val videoFormat = p.currentTracks.groups.find { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }?.getTrackFormat(0)
        val audioFormat = p.currentTracks.groups.find { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }?.getTrackFormat(0)

        if (currentState is PlayerUiState.Playing) {
            _uiState.value = currentState.copy(
                position = p.currentPosition,
                duration = if (p.duration > 0) p.duration else 0L,
                isPlaying = p.playWhenReady,
                isReconnecting = false,
                contentType = currentType,
                isLive = isLive,
                name = currentState.name,
                streamIcon = resolvedIcon,
                nextEpisodeStreamId = nextEpisodeStreamId,
                nextEpisodeName = nextEpisodeName,
                tracks = p.currentTracks,
                availableQualities = _availableQualities.value,
                epgId = currentEpgId,
                streamId = currentStreamId,
                resolution = videoFormat?.let { "${it.width}x${it.height}" },
                frameRate = videoFormat?.frameRate,
                videoCodec = videoFormat?.sampleMimeType,
                audioCodec = audioFormat?.sampleMimeType,
                playbackSpeed = if (p.playbackParameters.speed > 0f) p.playbackParameters.speed else _playbackSpeed.value
            )
        } else {
            _uiState.value = PlayerUiState.Playing(
                position = p.currentPosition,
                duration = if (p.duration > 0) p.duration else 0L,
                isPlaying = p.playWhenReady,
                contentType = currentType,
                isLive = isLive,
                name = currentDisplayName ?: currentState.name,
                streamIcon = resolvedIcon,
                nextEpisodeStreamId = nextEpisodeStreamId,
                nextEpisodeName = nextEpisodeName,
                tracks = p.currentTracks,
                availableQualities = _availableQualities.value,
                epgId = currentEpgId,
                streamId = currentStreamId,
                resolution = videoFormat?.let { "${it.width}x${it.height}" },
                frameRate = videoFormat?.frameRate,
                videoCodec = videoFormat?.sampleMimeType,
                audioCodec = audioFormat?.sampleMimeType,
                playbackSpeed = if (p.playbackParameters.speed > 0f) p.playbackParameters.speed else _playbackSpeed.value
            )
        }
    }

    private fun handlePlaybackEvent(event: PlaybackManager.Event) {
        val p = playbackManager.player ?: return
        val currentSession = _zappingSessionId.value

        val eventMediaId = when (event) {
            is PlaybackManager.Event.PlaybackStateChanged -> event.mediaId
            is PlaybackManager.Event.TracksChanged -> event.mediaId
            is PlaybackManager.Event.IsPlayingChanged -> event.mediaId
            is PlaybackManager.Event.PlayerError -> event.mediaId
            is PlaybackManager.Event.RenderedFirstFrame -> event.mediaId
            else -> null
        }

        val expectedMediaId = currentStreamId?.toString()

        // Caso A: Evento com mediaId explícito que não corresponde ao canal ativo esperado -> rejeitar
        if (!eventMediaId.isNullOrBlank() && expectedMediaId != null && eventMediaId != expectedMediaId) {
            Timber.w("[PLAYBACK_EVENT_REJECTED] reason=STALE_MEDIA_ID_MISMATCH eventMediaId=$eventMediaId expectedMediaId=$expectedMediaId session=$currentSession")
            return
        }

        // Caso B: Evento sem mediaId (mediaId == null) emitido durante cleanup de transição em sessão ativa
        if (eventMediaId == null && expectedMediaId != null) {
            when (event) {
                is PlaybackManager.Event.PlayerError -> {
                    // Erros de fechamento de socket/cleanup da mídia anterior com mediaId=null NÃO devem afetar o canal novo nem ativar recovery
                    Timber.w("[PLAYBACK_EVENT_REJECTED] reason=CLEANUP_ERROR_NULL_MEDIA_ID session=$currentSession activeStream=$expectedMediaId")
                    return
                }
                is PlaybackManager.Event.PlaybackStateChanged -> {
                    if (event.state != Player.STATE_IDLE) {
                        Timber.w("[PLAYBACK_EVENT_REJECTED] reason=NULL_MEDIA_ID_NON_IDLE_STATE state=${event.state} session=$currentSession activeStream=$expectedMediaId")
                        return
                    }
                }
                is PlaybackManager.Event.TracksChanged,
                is PlaybackManager.Event.IsPlayingChanged,
                is PlaybackManager.Event.RenderedFirstFrame -> {
                    Timber.w("[PLAYBACK_EVENT_REJECTED] reason=NULL_MEDIA_ID_GENERIC_EVENT session=$currentSession activeStream=$expectedMediaId event=${event.javaClass.simpleName}")
                    return
                }
                else -> {}
            }
        }

        if (!isPlaybackEventValid(currentSession, eventMediaId)) {
            return
        }
        
        when (event) {
            is PlaybackManager.Event.PlaybackStateChanged -> {
                Timber.d("[session=$currentSession][stream=$currentStreamId] stateChanged -> state=${event.state} (READY=3, BUFFERING=2, IDLE=1, ENDED=4), isPlaying=${p.isPlaying}, mediaId=$eventMediaId")
                when (event.state) {
                    Player.STATE_BUFFERING -> {
                        rebufferingCount++
                        val currentName = (_uiState.value as? PlayerUiState.Playing)?.name ?: _uiState.value.name
                        if (!p.isPlaying && p.playbackState != Player.STATE_READY) {
                            _uiState.value = PlayerUiState.Loading(name = currentName)
                        }
                    }
                    Player.STATE_IDLE -> {
                        // Event-driven state update
                    }
                    Player.STATE_READY -> {
                        val now = System.currentTimeMillis()
                        zapStateReadyMs = now
                        val readyLat = now - zapStartTimeMs
                        Timber.d("[ZAP_LATENCY][session=$currentSession][stream=$currentStreamId] STATE_READY em ${readyLat}ms")
                        stopWatchdog("STATE_READY_REACHED")
                        if (p.currentPosition > 1000) {
                            recoveryCount = 0
                            consecutiveStallCount = 0
                        }
                        _nextEpisodeCountdown.value = null
                        updatePlayingState(p, currentSession)
                    }
                    Player.STATE_ENDED -> {
                        stopWatchdog("STATE_ENDED_REACHED")
                        viewModelScope.launch {
                            if (!isPlaybackEventValid(currentSession, eventMediaId)) return@launch
                            val settings = settingsRepository.settingsFlow.first()
                            if (settings.autoPlayEnabled && currentType == ContentType.SERIES && nextEpisodeStreamId != null) {
                                startNextEpisodeCountdown()
                            }

                            if (currentType == ContentType.SERIES && currentSeriesId != null && currentSeasonNumber != null && currentStreamId != null) {
                                smartDownloadUseCase(currentSeriesId!!, currentSeasonNumber!!, currentStreamId!!)
                            }
                        }
                    }
                }
            }
            is PlaybackManager.Event.TracksChanged -> {
                // Only update tracks and apply preferences when player is in STATE_READY to prevent UI recomposition surface drops during initial buffering
                if (p.playbackState == Player.STATE_READY) {
                    val currentState = _uiState.value
                    if (currentState is PlayerUiState.Playing && currentState.streamId == currentStreamId) {
                        _uiState.value = currentState.copy(tracks = event.tracks)
                    }
                    applyTrackPreferences(event.tracks)
                }
            }
            is PlaybackManager.Event.RenderedFirstFrame -> {
                val now = System.currentTimeMillis()
                val totalLat = if (zapStartTimeMs > 0) now - zapStartTimeMs else 0L
                val stopDur = if (zapStopDoneMs > 0) zapStopDoneMs - zapStartTimeMs else 0L
                val urlDur = if (zapUrlReadyMs > 0) zapUrlReadyMs - zapStopDoneMs else 0L
                val prepDur = if (zapPreparedMs > 0) zapPreparedMs - zapUrlReadyMs else 0L
                val readyDur = if (zapStateReadyMs > 0) zapStateReadyMs - zapPreparedMs else 0L
                val renderDur = if (zapStateReadyMs > 0) now - zapStateReadyMs else 0L

                val rating = when {
                    totalLat in 1..499 -> "EXCELENTE"
                    totalLat in 500..999 -> "BOM"
                    totalLat in 1000..1499 -> "ACEITÁVEL"
                    totalLat in 1500..2499 -> "LENTO"
                    totalLat >= 2500 -> "INVESTIGAR"
                    else -> "N/A"
                }

                Timber.i("[ZAP_LATENCY] channel='$currentDisplayName' TOTAL=$totalLat ms ($rating) -> stop=${stopDur}ms, url=${urlDur}ms, prep=${prepDur}ms, ready=${readyDur}ms, render=${renderDur}ms")
            }
            is PlaybackManager.Event.IsPlayingChanged -> {
                Timber.d("[session=$currentSession][stream=$currentStreamId] isPlayingChanged -> isPlaying=${event.isPlaying}, state=${p.playbackState}, mediaId=$eventMediaId")
                if (event.isPlaying || p.playbackState == Player.STATE_READY) {
                    stopWatchdog("IS_PLAYING_OR_READY")
                    updatePlayingState(p, currentSession)
                } else {
                    val currentState = _uiState.value
                    if (currentState is PlayerUiState.Playing && currentState.streamId == currentStreamId) {
                        _uiState.value = currentState.copy(isPlaying = event.isPlaying)
                    }
                }
            }
            is PlaybackManager.Event.PlayerError -> {
                stopWatchdog("PLAYER_ERROR")
                val error = event.error
                val cause = error.cause
                val httpResponseCode = (cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode ?: -1

                val isHttpDefiniteError = httpResponseCode in listOf(401, 403, 404) ||
                        (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
                                (cause?.message?.contains("401") == true || cause?.message?.contains("403") == true || cause?.message?.contains("404") == true))

                val isTransientNetworkError = error.errorCode in listOf(
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
                ) || cause is java.io.IOException

                Timber.e(error, "[PLAYER_ERROR][session=$currentSession][stream=$currentStreamId] errorCode=${error.errorCode} httpCode=$httpResponseCode transient=$isTransientNetworkError definite=$isHttpDefiniteError")

                if (isHttpDefiniteError) {
                    val message = when (httpResponseCode) {
                        401, 403 -> "Acesso negado pelo servidor (HTTP $httpResponseCode). Verifique seu limite de telas ou assinatura."
                        404 -> "Transmissão não encontrada no servidor (HTTP 404)."
                        else -> "Erro de servidor (HTTP $httpResponseCode)."
                    }
                    Timber.w("[HTTP_ERROR_NO_RECOVERY] httpCode=$httpResponseCode session=$currentSession stream=$currentStreamId")
                    _uiState.value = PlayerUiState.Error(message)
                    return
                }

                if (isTransientNetworkError && currentType == ContentType.LIVE && currentStreamId != null) {
                    if (recoveryCount >= 1) {
                        Timber.w("[RECOVERY_FAILED] Máximo de 1 tentativa atingido. channelSwitchId=$currentChannelSwitchId")
                        _uiState.value = PlayerUiState.Error("Falha na conexão de rede. Toque para tentar novamente.")
                        return
                    }
                    Timber.i("[RECOVERY_NETWORK_TRANSIENT] Executando 1 tentativa automática de reconexão para stream $currentStreamId")
                    performRecovery(currentSession, currentStreamId, currentChannelSwitchId ?: "default", "PLAYER_ERROR_${error.errorCode}")
                    return
                }

                Timber.w("[PLAYER_ERROR_UNCLASSIFIED] errorCode=${error.errorCode}")
                _uiState.value = PlayerUiState.Error(application.getString(R.string.stream_playback_error))
            }
            else -> {}
        }
    }

    private fun startWatchdog(sessionId: Int = _zappingSessionId.value, streamId: Int? = currentStreamId, channelSwitchId: String? = currentChannelSwitchId) {
        if (streamId == null || channelSwitchId == null) return
        playbackWatchdog.startMonitoring(
            player = playbackManager.player ?: return,
            contentType = currentType,
            sessionId = sessionId,
            streamId = streamId,
            channelSwitchId = channelSwitchId,
            scope = viewModelScope
        ) {}
    }

    private fun handleWatchdogHang(sessionId: Int, streamId: Int, channelSwitchId: String, reason: String) {
        // Event-driven handle
    }

    private fun stopWatchdog(reason: String = "EXPLICIT_STOP") {
        playbackWatchdog.stop(reason)
    }

    private var isRecovering = false
    private var recoveryJob: Job? = null

    private fun performRecovery(sessionId: Int, streamId: Int? = currentStreamId, recoveryChannelSwitchId: String? = currentChannelSwitchId, reason: String = "TRANSIENT_NETWORK_ERROR") {
        if (streamId == null || recoveryChannelSwitchId != currentChannelSwitchId || !isPlaybackEventValid(sessionId, streamId.toString(), recoveryChannelSwitchId)) {
            Timber.w("[RECOVERY_ABORTED_STALE] session=$sessionId stream=$streamId channelSwitchId=$recoveryChannelSwitchId currentChannelSwitchId=$currentChannelSwitchId")
            return
        }
        
        if (recoveryJob?.isActive == true || isRecovering || recoveryCount >= 1) {
            Timber.w("[RECOVERY_ALREADY_ACTIVE_OR_EXHAUSTED] session=$sessionId stream=$streamId attempts=$recoveryCount")
            return
        }
        isRecovering = true
        recoveryCount = 1

        recoveryJob = viewModelScope.launch {
            try {
                if (recoveryChannelSwitchId != currentChannelSwitchId || !isPlaybackEventValid(sessionId, streamId.toString(), recoveryChannelSwitchId)) {
                    Timber.i("[RECOVERY_ABORTED_STALE] channelSwitchId=$recoveryChannelSwitchId != currentChannelSwitchId=$currentChannelSwitchId")
                    return@launch
                }

                val currentState = _uiState.value
                if (currentState is PlayerUiState.Playing && currentState.streamId == currentStreamId) {
                    _uiState.value = currentState.copy(isReconnecting = true)
                }

                Timber.i("[RECOVERY_ATTEMPT] Executando reload de emergência (Tentativa 1/1) channelSwitchId=$recoveryChannelSwitchId session=$sessionId stream=$streamId reason=$reason")
                reloadStream(isRecovery = true)

            } catch (e: CancellationException) {
                Timber.i("[RECOVERY_CANCELLED] channelSwitchId=$recoveryChannelSwitchId reason=CANCELLED_BY_COROUTINE")
            } finally {
                isRecovering = false
            }
        }
    }

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _nextEpisodeCountdown = MutableStateFlow<Int?>(null)
    val nextEpisodeCountdown: StateFlow<Int?> = _nextEpisodeCountdown.asStateFlow()

    private val _currentProgram = MutableStateFlow<EpgProgramme?>(null)
    val currentProgram: StateFlow<EpgProgramme?> = _currentProgram.asStateFlow()

    private val _nextPrograms = MutableStateFlow<List<EpgProgramme>>(emptyList())
    val nextPrograms: StateFlow<List<EpgProgramme>> = _nextPrograms.asStateFlow()

    private val _quickSwitchStreams = MutableStateFlow<List<XtreamStream>>(emptyList())
    val quickSwitchStreams: StateFlow<List<XtreamStream>> = _quickSwitchStreams.asStateFlow()

    private val _zappingEpg = MutableStateFlow<Map<Int, EpgProgramme>>(emptyMap())
    val zappingEpg: StateFlow<Map<Int, EpgProgramme>> = _zappingEpg.asStateFlow()

    private val _recentChannels = MutableStateFlow<List<XtreamStream>>(emptyList())
    val recentChannels: StateFlow<List<XtreamStream>> = _recentChannels.asStateFlow()

    private val _sleepTimer = MutableStateFlow<Int?>(null)
    val sleepTimer: StateFlow<Int?> = _sleepTimer.asStateFlow()
    private var sleepTimerJob: Job? = null

    private val _subtitleSize = MutableStateFlow(1f) // 1.0f is normal
    val subtitleSize: StateFlow<Float> = _subtitleSize.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    val appSettings: StateFlow<SettingsRepository.AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsRepository.AppSettings(null, lastSyncTimestamp = 0L, syncIntervalHours = 24)
    )

    private val _resizeMode = MutableStateFlow(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT)
    val resizeMode: StateFlow<Int> = _resizeMode.asStateFlow()

    private val _availableQualities = MutableStateFlow<Map<String, Int>>(emptyMap())
    val availableQualities: StateFlow<Map<String, Int>> = _availableQualities.asStateFlow()

    private var recoveryCount = 0
    private var consecutiveStallCount = 0
    
    private var positionUpdateJob: Job? = null
    private var currentType: ContentType = ContentType.LIVE
    private var currentContainer: String? = null
    private var currentSeriesId: Int? = null
    private var currentSeasonNumber: Int? = null
    private var currentStreamId: Int? = null
    private var currentEpgId: String? = null
    private var currentCategoryId: String? = null
    private var nextEpisodeStreamId: Int? = null
    private var nextEpisodeName: String? = null
    private var savedProgressPosition: Long? = null
    private var lastQualities: Map<String, Int> = emptyMap()

    private var rebufferingCount = 0
    private var lastSignalCheck = System.currentTimeMillis()
    private var dnsRetryCount = 0
    private val MAX_DNS_RETRIES = 3

    sealed class NavigationEvent {
        data class NavigateToPlayer(
            val streamId: Int,
            val name: String,
            val type: ContentType,
            val container: String?
        ) : NavigationEvent()

        data class OpenExternalPlayer(
            val url: String,
            val title: String
        ) : NavigationEvent()
    }

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private var playJob: Job? = null
    private var backgroundJob: Job? = null

    private var currentDisplayName: String? = null
    private var currentStreamIcon: String? = null

    private var zapStartTimeMs: Long = 0L
    private var zapStopDoneMs: Long = 0L
    private var zapUrlReadyMs: Long = 0L
    private var zapPreparedMs: Long = 0L
    private var zapStateReadyMs: Long = 0L

    val isLiveContent: Boolean get() = currentType == ContentType.LIVE

    fun playStream(
        streamId: Int, 
        type: ContentType, 
        container: String?, 
        epgId: String?,
        seriesId: Int? = null,
        seasonNumber: Int? = null,
        qualities: Map<String, Int> = emptyMap(),
        categoryId: String? = null,
        displayName: String? = null,
        streamIcon: String? = null,
        isRecovery: Boolean = false,
        isZapping: Boolean = false,
        existingChannelSwitchId: String? = null
    ) {
        val t0 = System.currentTimeMillis()
        zapStartTimeMs = t0

        currentDisplayName = displayName
        currentStreamIcon = streamIcon

        val csId = if (isRecovery && !existingChannelSwitchId.isNullOrBlank()) {
            existingChannelSwitchId
        } else {
            DebugCorrelation.newChannelSwitchId(streamId)
        }
        currentChannelSwitchId = csId
        val previousChannel = currentStreamId?.toString() ?: "none"
        DebugLogger.selectedChannelId.set(streamId.toString())
        DebugLogger.activeChannelSwitchId.set(csId)

        DebugLogger.startOperation(
            operationId = csId,
            name = "CHANNEL_SWITCH",
            category = DebugCategory.CHANNEL_SWITCH,
            timeoutMs = 15_000L,
            channelSwitchId = csId,
            context = mapOf(
                "targetChannelId" to streamId.toString(),
                "previousChannelId" to previousChannel,
                "displayName" to (displayName ?: "unknown"),
                "isZapping" to isZapping.toString(),
                "isRecovery" to isRecovery.toString()
            )
        )

        DebugLogger.log(
            level = DebugLevel.INFO,
            category = DebugCategory.CHANNEL_SWITCH,
            event = "CHANNEL_SWITCH_START",
            channelSwitchId = csId,
            channelId = streamId.toString(),
            context = mapOf(
                "previousChannel" to previousChannel,
                "targetChannel" to streamId.toString(),
                "displayName" to (displayName ?: "unknown")
            )
        )

        val oldSession = _zappingSessionId.value
        val newSession = oldSession + 1
        _zappingSessionId.value = newSession

        Timber.i("[PLAYER_SESSION] release sessionId=$oldSession stream=$currentStreamId reason=CHANNEL_CHANGE")
        Timber.i("[PLAYER_SESSION] create sessionId=$newSession stream=$streamId channel=$displayName")

        // Cancel previous play job, background job, recovery job and watchdog immediately
        playJob?.cancel()
        backgroundJob?.cancel()
        recoveryJob?.cancel()
        epgJob?.cancel()
        isRecovering = false
        stopWatchdog("NEW_STREAM_STARTED")
        
        // Immediately stop previous player session and release previous HTTP stream socket on Main thread
        // BEFORE starting new stream URL request, preventing overlapping active connection count on server!
        DebugLogger.log(
            level = DebugLevel.DEBUG,
            category = DebugCategory.CHANNEL_SWITCH,
            event = "CHANNEL_SWITCH_STOP_OLD",
            channelSwitchId = csId,
            channelId = streamId.toString()
        )
        playbackManager.stopAndClear()
        zapStopDoneMs = System.currentTimeMillis()

        if (!isRecovery) {
            recoveryCount = 0
            consecutiveStallCount = 0
            dnsRetryCount = 0
        }

        _playbackSpeed.value = 1.0f
        playbackManager.setPlaybackSpeed(1.0f)
        
        currentStreamId = streamId
        currentType = type
        currentContainer = container
        currentEpgId = epgId
        currentSeriesId = seriesId
        currentSeasonNumber = seasonNumber
        currentCategoryId = categoryId
        nextEpisodeStreamId = null
        nextEpisodeName = null

        if (type == ContentType.LIVE && streamId > 0) {
            viewModelScope.launch {
                settingsRepository.updateLastChannel("live", categoryId ?: "", streamId.toString(), displayName ?: "")
            }
        }
        lastQualities = qualities
        _availableQualities.value = qualities

        // Update UI State instantly with metadata even before loading starts
        if (type == ContentType.LIVE) {
            val currentState = _uiState.value
            val isFav = if (currentState is PlayerUiState.Playing) currentState.isFavorite else false
            
            _uiState.value = PlayerUiState.Playing(
                isPlaying = false,
                contentType = type,
                isLive = true,
                epgId = epgId,
                streamId = streamId,
                isFavorite = isFav,
                name = displayName ?: "Carregando...",
                streamIcon = streamIcon
            )
            
            // Clear current EPG to avoid showing old info
            _currentProgram.value = null
            _nextPrograms.value = emptyList()
        } else {
            _uiState.value = PlayerUiState.Loading(name = displayName)
        }

        playJob = viewModelScope.launch {
            val currentSession = _zappingSessionId.value
            try {
                if (!isPlaybackEventValid(currentSession, streamId.toString())) return@launch
                val credentials = appSettings.value.credentials 
                    ?: settingsRepository.settingsFlow.first().credentials 
                    ?: run {
                        _uiState.value = PlayerUiState.Error("Credenciais não encontradas")
                        return@launch
                    }
                
                if (!isPlaybackEventValid(currentSession, streamId.toString())) return@launch
                val actualName = displayName ?: "Carregando..."
                val actualIcon = streamIcon

                val tStreamStart = System.currentTimeMillis()
                Timber.d("[MEDIA_NEW_STREAM_START] session=$currentSession stream=$streamId name='$actualName' timestamp=$tStreamStart")
                
                // Get URL immediately (fast)
                val url = streamRepository.getStreamUrl(credentials, streamId, type.toString(), container)
                zapUrlReadyMs = System.currentTimeMillis()
                
                ensurePlayer()
                
                withContext(Dispatchers.Main) {
                    ensureActive()
                    if (!isPlaybackEventValid(currentSession, streamId.toString())) {
                        Timber.w("[STALE_JOB_ABORTED] session=$currentSession != activeSession=${_zappingSessionId.value}. Abortando preparo de mídia para stream $streamId.")
                        return@withContext
                    }

                    val metadataBuilder = MediaMetadata.Builder()
                        .setTitle(actualName)
                        .setDisplayTitle(actualName)
                        .setArtist(when (type) {
                            ContentType.LIVE -> application.getString(R.string.live_tv_title)
                            ContentType.SERIES -> application.getString(R.string.series_title)
                            ContentType.MOVIE -> application.getString(R.string.movies_title)
                            else -> "Squi TV"
                        })
                        .setIsPlayable(true)

                    if (!actualIcon.isNullOrBlank()) {
                        try {
                            metadataBuilder.setArtworkUri(Uri.parse(actualIcon))
                        } catch (_: Exception) {}
                    }

                    val mediaItem = MediaItem.Builder()
                        .setUri(url)
                        .setMediaId(streamId.toString())
                        .setMediaMetadata(metadataBuilder.build())
                        .setLiveConfiguration(
                            MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(if (type == ContentType.LIVE) 2000L else 5000L) 
                                .build()
                        )
                        .build()

                    playbackManager.setMediaItem(mediaItem)
                    playbackManager.prepare()
                    playbackManager.play()
                    zapPreparedMs = System.currentTimeMillis()
                    
                    Timber.d("[MEDIA_TRANSITION_COMPLETE] session=$currentSession stream=$streamId timestamp=${zapPreparedMs}")

                    startWatchdog(currentSession, streamId, csId)

                    val p = playbackManager.player
                    if (p != null && (p.isPlaying || p.playbackState == Player.STATE_READY)) {
                        updatePlayingState(p, currentSession)
                    }
                }

                if (!isPlaybackEventValid(currentSession, streamId.toString())) return@launch

                // Background tasks after starting playback
                backgroundJob = launch(Dispatchers.IO) {
                    if (type == ContentType.LIVE && isPlaybackEventValid(currentSession, streamId.toString())) {
                        val streams = catalogRepository.getStreamsByIds(listOf(streamId))
                        if (!isPlaybackEventValid(currentSession, streamId.toString())) return@launch
                        val stream = streams.firstOrNull { it.streamType == type.toString().uppercase() }
                        val isFav = stream?.isFavorite ?: false
                        val dbName = stream?.name ?: actualName
                        val dbIcon = stream?.logo ?: actualIcon

                        withContext(Dispatchers.Main) {
                            if (isPlaybackEventValid(currentSession, streamId.toString())) {
                                val currentState = _uiState.value
                                if (currentState is PlayerUiState.Playing && currentState.streamId == streamId) {
                                    _uiState.value = currentState.copy(
                                        isFavorite = isFav,
                                        name = dbName,
                                        streamIcon = dbIcon
                                    )
                                }
                            }
                        }

                        if (stream != null && isPlaybackEventValid(currentSession, streamId.toString())) {
                            withContext(Dispatchers.Main) {
                                if (isPlaybackEventValid(currentSession, streamId.toString())) {
                                    addToRecentChannels(XtreamStream(
                                        streamId = stream.id,
                                        name = stream.name,
                                        streamIcon = stream.logo,
                                        epgChannelId = stream.url,
                                        categoryId = stream.categoryId,
                                        streamType = stream.streamType
                                    ))
                                }
                            }
                        }
                        if (isPlaybackEventValid(currentSession, streamId.toString())) {
                            loadEpg(streamId)
                            if (_quickSwitchStreams.value.isEmpty() || lastLoadedCategoryId != categoryId) {
                                lastLoadedCategoryId = categoryId
                                loadQuickSwitchStreams(categoryId)
                            }
                        }
                    }
                }
                
                if (type == ContentType.SERIES && seriesId != null && seasonNumber != null && isPlaybackEventValid(currentSession, streamId.toString())) {
                    checkNextEpisode(seriesId, seasonNumber, streamId)
                }

                if (isPlaybackEventValid(currentSession, streamId.toString())) {
                    startPositionUpdates()
                }
            } catch (e: CancellationException) {
                Timber.d("[MEDIA_RELEASE_WINDOW_CANCELLED] session=$currentSession stream=$streamId reason=newer_request timestamp=${System.currentTimeMillis()}")
            } catch (e: Exception) {
                if (isPlaybackEventValid(currentSession, streamId.toString())) {
                    Timber.e(e, "Erro ao iniciar stream")
                    if (type == ContentType.LIVE) {
                        _uiState.value = PlayerUiState.Error("Conexão falhou. Tentando reconectar...")
                    } else {
                        _uiState.value = PlayerUiState.Error("Erro ao reproduzir: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    fun reloadStream(isRecovery: Boolean = false) {
        val streamId = currentStreamId ?: return
        playStream(
            streamId = streamId,
            type = currentType,
            container = currentContainer,
            epgId = currentEpgId,
            seriesId = currentSeriesId,
            seasonNumber = currentSeasonNumber,
            qualities = lastQualities,
            categoryId = currentCategoryId,
            displayName = currentDisplayName,
            streamIcon = currentStreamIcon,
            isRecovery = isRecovery,
            existingChannelSwitchId = currentChannelSwitchId
        )
    }

    private fun addToRecentChannels(stream: XtreamStream) {
        val currentList = _recentChannels.value.toMutableList()
        currentList.removeAll { it.streamId == stream.streamId }
        currentList.add(0, stream)
        _recentChannels.value = currentList.take(10)
    }

    fun setSleepTimer(minutes: Int?) {
        _sleepTimer.value = minutes
        sleepTimerJob?.cancel()
        if (minutes != null && minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                var remaining = minutes
                while (remaining > 0) {
                    delay(60.seconds)
                    remaining--
                    _sleepTimer.value = remaining
                }
                stopPlayback()
                _sleepTimer.value = null
            }
        }
    }

    fun setSubtitleSize(size: Float) {
        _subtitleSize.value = size
    }

    private fun checkNextEpisode(seriesId: Int, seasonNumber: Int, streamId: Int) {
        viewModelScope.launch {
            try {
                val credentials = settingsRepository.settingsFlow.first().credentials ?: return@launch
                val (_, episodesMap) = catalogRepository.getSeriesInfo(credentials, seriesId)
                val episodes = episodesMap[seasonNumber] ?: emptyList()
                val currentIndex = episodes.indexOfFirst { it.streamId == streamId }
                val nextEpisode = if (currentIndex != -1 && currentIndex < episodes.size - 1) {
                    episodes[currentIndex + 1]
                } else null
                
                nextEpisodeStreamId = nextEpisode?.streamId
                nextEpisodeName = nextEpisode?.title

                val currentState = _uiState.value
                if (currentState is PlayerUiState.Playing) {
                    _uiState.value = currentState.copy(
                        nextEpisodeStreamId = nextEpisodeStreamId,
                        nextEpisodeName = nextEpisodeName
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                val p = playbackManager.player ?: break
                if (p.isPlaying || p.playbackState == Player.STATE_READY) {
                    val currentState = _uiState.value
                    if (currentState !is PlayerUiState.Playing) {
                        Timber.i("[PLAYBACK] Player ativo/pronto mas UI estava em Loading. Auto-corrigindo estado da UI para Playing.")
                        updatePlayingState(p)
                    } else {
                        val currentPos = p.currentPosition
                        val duration = p.duration
                        if (currentType != ContentType.LIVE && currentStreamId != null && duration > 0) {
                            syncWatchProgressUseCase(
                                streamId = currentStreamId!!,
                                type = currentType.toString(),
                                position = currentPos,
                                duration = duration,
                                seriesId = currentSeriesId
                            )
                        }
                        
                        val bandwidth = bandwidthMeter.bitrateEstimate / 1_000_000.0
                        val bufferedPos = p.bufferedPosition
                        val delay = if (p.isCurrentMediaItemLive) (p.duration - currentPos) / 1000 else (bufferedPos - currentPos) / 1000
                        
                        // Signal Health Logic
                        val now = System.currentTimeMillis()
                        if (now - lastSignalCheck > 30000) { // Every 30s check stability
                            rebufferingCount = 0
                            lastSignalCheck = now
                        }
                        val health = (1.0f - (rebufferingCount * 0.15f)).coerceIn(0.1f, 1.0f)

                        val videoFormat = p.currentTracks.groups.find { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }?.getTrackFormat(0)
                        val audioFormat = p.currentTracks.groups.find { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }?.getTrackFormat(0)

                        _uiState.value = currentState.copy(
                            position = currentPos,
                            duration = if (duration > 0) duration else 0L,
                            savedPosition = if (currentPos < 1000) savedProgressPosition else currentState.savedPosition,
                            bandwidthMbps = bandwidth,
                            bufferDelaySeconds = delay.coerceAtLeast(0),
                            bufferedPosition = bufferedPos,
                            resolution = videoFormat?.let { "${it.width}x${it.height}" },
                            frameRate = videoFormat?.frameRate,
                            videoCodec = videoFormat?.sampleMimeType,
                            audioCodec = audioFormat?.sampleMimeType,
                            signalHealth = health
                        )
                    }
                }
                delay(2000)
            }
        }
    }

    fun togglePlayPause() {
        if (playbackManager.player?.isPlaying == true) {
            playbackManager.pause()
        } else {
            playbackManager.play()
        }
    }

    fun seekTo(position: Long) {
        playbackManager.seekTo(position)
    }

    fun seekForward() {
        playbackManager.player?.let { p ->
            playbackManager.seekTo((p.currentPosition + 10000).coerceAtMost(p.duration))
        }
    }

    fun seekBack() {
        playbackManager.player?.let { p ->
            playbackManager.seekTo((p.currentPosition - 10000).coerceAtLeast(0L))
        }
    }

    fun playByNumber(number: String) {
        viewModelScope.launch {
            try {
                val num = number.toIntOrNull() ?: return@launch
                val streams = _quickSwitchStreams.value
                if (streams.isEmpty()) return@launch
                
                // Try to find by 'num' property first, then by index
                val target = streams.find { it.num == num } 
                    ?: if (num > 0 && num <= streams.size) streams[num - 1] else null
                
                target?.let {
                    playStream(
                        streamId = it.streamId ?: 0,
                        type = ContentType.LIVE,
                        container = null,
                        epgId = it.epgChannelId,
                        categoryId = currentCategoryId,
                        displayName = it.name,
                        streamIcon = it.streamIcon,
                        isZapping = true
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Erro ao reproduzir por número")
            }
        }
    }

    fun playNextChannel() {
        val streams = _quickSwitchStreams.value
        val currentId = currentStreamId
        val currentIndex = if (currentId != null) streams.indexOfFirst { it.streamId == currentId } else -1
        Timber.d("[ZAPPING_TRACE] NEXT requested -> streams.size=${streams.size}, currentStreamId=$currentId, currentIndex=$currentIndex, session=${_zappingSessionId.value}")
        if (streams.isEmpty()) {
            Timber.w("[ZAPPING] Lista de zapping vazia. Carregando canais em background...")
            viewModelScope.launch {
                var loaded = getQuickSwitchUseCase.getStreams(currentCategoryId)
                if (loaded.isEmpty() && currentCategoryId != null) {
                    loaded = getQuickSwitchUseCase.getStreams(null)
                }
                if (loaded.isNotEmpty()) {
                    _quickSwitchStreams.value = loaded
                    playNextChannel()
                } else {
                    Timber.e("[ZAPPING] Nenhuma stream encontrada para zapping.")
                }
            }
            return
        }
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % streams.size
        val target = streams[nextIndex]
        
        Timber.i("[ZAPPING] NEXT -> currentId=$currentId (index $currentIndex) -> targetId=${target.streamId} '${target.name}' (index $nextIndex of ${streams.size}, Session: ${_zappingSessionId.value + 1})")
        showZappingBanner(target)
        
        playStream(
            streamId = target.streamId ?: 0,
            type = ContentType.LIVE,
            container = null,
            epgId = target.epgChannelId,
            categoryId = currentCategoryId,
            displayName = target.name,
            streamIcon = target.streamIcon,
            isZapping = true
        )
    }

    fun playPreviousChannel() {
        val streams = _quickSwitchStreams.value
        val currentId = currentStreamId
        val currentIndex = if (currentId != null) streams.indexOfFirst { it.streamId == currentId } else -1
        Timber.d("[ZAPPING_TRACE] PREVIOUS requested -> streams.size=${streams.size}, currentStreamId=$currentId, currentIndex=$currentIndex, session=${_zappingSessionId.value}")
        if (streams.isEmpty()) {
            Timber.w("[ZAPPING] Lista de zapping vazia. Carregando canais em background...")
            viewModelScope.launch {
                var loaded = getQuickSwitchUseCase.getStreams(currentCategoryId)
                if (loaded.isEmpty() && currentCategoryId != null) {
                    loaded = getQuickSwitchUseCase.getStreams(null)
                }
                if (loaded.isNotEmpty()) {
                    _quickSwitchStreams.value = loaded
                    playPreviousChannel()
                } else {
                    Timber.e("[ZAPPING] Nenhuma stream encontrada para zapping.")
                }
            }
            return
        }
        val prevIndex = if (currentIndex <= 0) streams.size - 1 else currentIndex - 1
        val target = streams[prevIndex]
        
        Timber.i("[ZAPPING] PREVIOUS -> currentId=$currentId (index $currentIndex) -> targetId=${target.streamId} '${target.name}' (index $prevIndex of ${streams.size}, Session: ${_zappingSessionId.value + 1})")
        showZappingBanner(target)
        
        playStream(
            streamId = target.streamId ?: 0,
            type = ContentType.LIVE,
            container = null,
            epgId = target.epgChannelId,
            categoryId = currentCategoryId,
            displayName = target.name,
            streamIcon = target.streamIcon,
            isZapping = true
        )
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        playbackManager.setPlaybackSpeed(speed)
        val currentState = _uiState.value
        if (currentState is PlayerUiState.Playing) {
            _uiState.value = currentState.copy(playbackSpeed = speed)
        }
    }

    fun toggleResizeMode() {
        _resizeMode.value = when (_resizeMode.value) {
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun startNextEpisodeCountdown() {
        viewModelScope.launch {
            for (i in 5 downTo 1) {
                _nextEpisodeCountdown.value = i
                delay(1000)
            }
            _nextEpisodeCountdown.value = null
            playNextEpisode()
        }
    }

    fun cancelCountdown() {
        _nextEpisodeCountdown.value = null
    }

    fun playNextEpisode() {
        val currentState = _uiState.value as? PlayerUiState.Playing
        val nextId = currentState?.nextEpisodeStreamId ?: return
        val nextName = currentState.nextEpisodeName ?: "Next Episode"
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.NavigateToPlayer(nextId, nextName, currentType, currentContainer))
        }
    }

    fun toggleFavorite() {
        val streamId = currentStreamId ?: return
        val currentState = _uiState.value as? PlayerUiState.Playing ?: return
        val newFav = !currentState.isFavorite
        viewModelScope.launch {
            try {
                val streams = catalogRepository.getStreamsByIds(listOf(streamId))
                val stream = streams.firstOrNull { it.streamType == currentType.toString().uppercase() }
                if (stream != null) {
                    val item = IptvItem(
                        id = stream.id.toString(),
                        name = stream.name,
                        icon = stream.logo,
                        type = currentType,
                        epgId = stream.url,
                        rating = stream.rating,
                        releaseDate = stream.releaseDate,
                        containerExtension = stream.containerExtension,
                        categoryId = stream.categoryId
                    )
                    
                    val settings = settingsRepository.settingsFlow.first()
                    val profileId = settings.activeProfileId
                    val hash = settings.credentials?.providerHash
                    
                    toggleFavoriteUseCase(item, newFav, profileId, hash)
                    _uiState.value = currentState.copy(isFavorite = newFav)
                }
            } catch (e: Exception) {
                Timber.e(e, "Erro ao favoritar")
            }
        }
    }

    private fun loadEpg(streamId: Int) {
        epgJob?.cancel()
        val currentSession = _zappingSessionId.value
        epgJob = viewModelScope.launch {
            try {
                val result = getPlayerEpgUseCase(streamId)
                if (_zappingSessionId.value != currentSession) return@launch
                
                _currentProgram.value = result.current
                _nextPrograms.value = result.next
                
                val currentState = _uiState.value
                if (currentState is PlayerUiState.Playing && currentState.streamId == streamId) {
                    _uiState.value = currentState.copy(epgListings = result.allListings)
                }
            } catch (e: CancellationException) {
                // Expected
            } catch (e: Exception) {
                if (_zappingSessionId.value != currentSession) return@launch
                Timber.e(e, "Erro ao carregar EPG")
                _currentProgram.value = null
            }
        }
    }

    fun stopPlayback() {
        syncFinalProgress()
        stopWatchdog()
        playbackManager.stopAndDisconnect()
        positionUpdateJob?.cancel()
    }

    private fun syncFinalProgress() {
        val p = playbackManager.player ?: return
        val currentPos = p.currentPosition
        val duration = p.duration
        val streamId = currentStreamId
        if (currentType != ContentType.LIVE && streamId != null && duration > 0) {
            viewModelScope.launch {
                syncWatchProgressUseCase(
                    streamId = streamId,
                    type = currentType.toString(),
                    position = currentPos,
                    duration = duration,
                    seriesId = currentSeriesId,
                    forceSync = true
                )
            }
        }
    }

    fun selectTrack(groupId: Int, trackIndex: Int, trackType: Int) {
        playbackManager.player?.let { p ->
            val tracks = p.currentTracks
            val groups = tracks.groups.filter { it.type == trackType }
            if (groupId < groups.size) {
                val trackGroup = groups[groupId]
                val format = trackGroup.getTrackFormat(trackIndex)
                
                // Save preference
                viewModelScope.launch {
                    if (trackType == C.TRACK_TYPE_AUDIO) {
                        settingsRepository.updatePreferredAudioLang(format.language)
                    } else if (trackType == C.TRACK_TYPE_TEXT) {
                        settingsRepository.updatePreferredSubtitleLang(format.language)
                    }
                }

                playbackManager.setTrackSelectionParameters(
                    p.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(trackGroup.mediaTrackGroup, trackIndex))
                        .build()
                )
            }
        }
    }

    private fun applyTrackPreferences(tracks: Tracks) {
        val player = playbackManager.player ?: return
        val settings = appSettings.value
        
        var paramsBuilder = player.trackSelectionParameters.buildUpon()
        var changed = false

        // Audio
        if (!settings.preferredAudioLang.isNullOrBlank()) {
            val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            audioGroups.forEach { group ->
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.language == settings.preferredAudioLang && !group.isTrackSelected(i)) {
                        paramsBuilder = paramsBuilder.setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, i)
                        )
                        changed = true
                        break
                    }
                }
            }
        }

        // Subtitles
        if (!settings.preferredSubtitleLang.isNullOrBlank()) {
            val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            textGroups.forEach { group ->
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.language == settings.preferredSubtitleLang && !group.isTrackSelected(i)) {
                        paramsBuilder = paramsBuilder.setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, i)
                        )
                        changed = true
                        break
                    }
                }
            }
        }

        if (changed) {
            playbackManager.setTrackSelectionParameters(paramsBuilder.build())
            // Correção cirúrgica: setTrackSelectionParameters aplica diretamente as preferências ao player.
            // NUNCA chamar prepare() ou play() quando o player está em STATE_IDLE sem MediaItem associado.
        }
    }

    fun clearTrackOverride(@Suppress("UNUSED_PARAMETER") trackType: Int) {
        playbackManager.clearTrackOverrides()
    }

    private var zappingEpgJob: Job? = null

    private fun loadQuickSwitchStreams(categoryId: String? = null) {
        zappingEpgJob?.cancel()
        zappingEpgJob = viewModelScope.launch {
            try {
                var streams = getQuickSwitchUseCase.getStreams(categoryId)
                if (streams.isEmpty() && categoryId != null) {
                    streams = getQuickSwitchUseCase.getStreams(null)
                }
                _quickSwitchStreams.value = streams
                
                getQuickSwitchUseCase.loadZappingEpg(streams).collect { (streamId, programme) ->
                    _zappingEpg.update { current ->
                        val next = current + (streamId to programme)
                        if (next.size > 50) next.toList().takeLast(50).toMap() else next
                    }
                }
            } catch (e: CancellationException) {
                // Expected when new zapping occurs
            } catch (e: Exception) {
                Timber.e(e, "Erro ao carregar streams de zapping")
            }
        }
    }

    override fun onCleared() {
        stopWatchdog()
        stopPlayback()
    }
}
