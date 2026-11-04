package rsv.squitv.desktop.player

import com.sun.jna.*
import com.sun.jna.platform.win32.Kernel32
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.awt.Canvas
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import rsv.squitv.shared.player.VideoScaleMode
import rsv.squitv.shared.player.VlcVideoGeometry
import rsv.squitv.shared.player.vlcGeometry

data class MediaTrack(val id: Int, val name: String)

data class PlayerStatus(val phase: String = "Preparando reprodução…", val paused: Boolean = false,
    val volume: Int = 80, val muted: Boolean = false, val error: String? = null,
    val timeMs: Long = 0, val durationMs: Long = 0, val seekable: Boolean = false, val ended: Boolean = false,
    val audioTracks: List<MediaTrack> = emptyList(), val subtitleTracks: List<MediaTrack> = emptyList(),
    val audioTrack: Int = -1, val subtitleTrack: Int = -1, val speed: Float = 1f,
    val scaleMode: VideoScaleMode = VideoScaleMode.FIT, val optionError: String? = null, val sourceId: Int? = null,
    val playbackGeneration: Long = 0)

/** The native API is owned by one worker. No stream address is logged or exposed in UI state. */
class WindowsLivePlayer(private val onDemand: Boolean = false) : AutoCloseable {
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "squitv-video").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)
    private val generation = AtomicLong()
    private val mutable = MutableStateFlow(PlayerStatus())
    val state = mutable.asStateFlow()
    private var api: LibVlc? = null
    private var instance: Pointer? = null
    private var player: Pointer? = null
    private var surface: Pointer? = null
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var appliedGeometry: VlcVideoGeometry? = null
    private data class PendingMedia(val address: String, val generation: Long,
        val audioLanguage: String?, val subtitleLanguage: String?)
    private var pendingMedia: PendingMedia? = null
    private var active = false
    private var activeGeneration = 0L

    init {
        worker.scheduleWithFixedDelay({ guarded {
            val p = player ?: return@guarded
            if (!active || activeGeneration != generation.get()) return@guarded
            val polledGeneration = activeGeneration
            val code = api!!.libvlc_media_player_get_state(p)
            // Native calls must stay outside StateFlow's retryable update lambda.
            val time = if (onDemand) api!!.libvlc_media_player_get_time(p).coerceAtLeast(0) else 0
            val duration = if (onDemand) api!!.libvlc_media_player_get_length(p).coerceAtLeast(0) else 0
            val seekable = onDemand && api!!.libvlc_media_player_is_seekable(p) != 0
            mutable.update { if (polledGeneration != generation.get()) it else it.copy(phase = when (code) {
                3 -> if (onDemand) "Reproduzindo" else "Ao vivo"; 4 -> "Pausado"
                6 -> if (onDemand) "Concluído" else "Transmissão encerrada"; 7 -> "Não foi possível reproduzir"
                else -> "Conectando…"
            }, paused = code == 4, error = when (code) {
                6 -> if (onDemand) null else "O servidor encerrou a transmissão. Tente reconectar."
                7 -> if (onDemand) "O conteúdo não respondeu ou o formato está indisponível. Tente novamente."
                    else "O canal não respondeu ou o formato está indisponível. Tente reconectar."
                else -> null
            }, timeMs = time, durationMs = duration, seekable = seekable,
                ended = onDemand && code == 6) }
            // Terminal states stay published until an explicit retry/new item; stop JNI polling.
            if (code == 6 || code == 7) active = false
        } }, 500, 500, TimeUnit.MILLISECONDS)
    }

    /** Call only on the AWT event thread, after the Canvas becomes displayable. */
    fun attach(canvas: Canvas) {
        if (closed.get() || !canvas.isDisplayable) return
        val handle = Native.getComponentPointer(canvas)
        submit {
            surface = handle
            player?.let { api!!.libvlc_media_player_set_hwnd(it, handle) }
            startPending()
        }
    }

    fun play(address: String, sourceId: Int? = null, audioLanguage: String? = null, subtitleLanguage: String? = null) {
        val token = generation.incrementAndGet()
        mutable.update { it.copy(phase = "Conectando…", paused = false, error = null,
            timeMs = 0, durationMs = 0, seekable = false, ended = false, sourceId = sourceId, playbackGeneration = token,
            audioTracks = emptyList(), subtitleTracks = emptyList(), speed = 1f, optionError = null) }
        submit {
            if (token != generation.get()) return@submit
            pendingMedia = PendingMedia(address, token, rsv.squitv.shared.player.TrackLanguage.normalize(audioLanguage),
                rsv.squitv.shared.player.TrackLanguage.normalize(subtitleLanguage))
            startPending()
        }
    }

    fun togglePause() = submit(mediaScoped = true) {
        player?.let {
            val paused = !mutable.value.paused
            api!!.libvlc_media_player_set_pause(it, if (paused) 1 else 0)
            mutable.update { current -> current.copy(paused = paused) }
        }
    }

    fun setVolume(volume: Int) = submit {
        val value = volume.coerceIn(0, 100)
        if (mutable.value.volume == value) return@submit
        player?.let { api!!.libvlc_audio_set_volume(it, value) }
        mutable.update { it.copy(volume = value) }
    }

    fun toggleMute() = submit {
        val muted = !mutable.value.muted
        player?.let { api!!.libvlc_audio_set_mute(it, if (muted) 1 else 0) }
        mutable.update { it.copy(muted = muted) }
    }

    fun seekTo(timeMs: Long) = submit(mediaScoped = true) {
        val status = mutable.value
        if (onDemand && status.seekable && status.durationMs > 0) player?.let {
            val target = timeMs.coerceIn(0, status.durationMs)
            api!!.libvlc_media_player_set_time(it, target)
            // The shared seek controller holds the visual target. Publish only polled native time
            // so history and pending-seek confirmation cannot treat a request as completed playback.
        }
    }

    fun refreshTracks() = submit(mediaScoped = true) {
        val p = player ?: return@submit
        val vlc = api ?: return@submit
        fun descriptions(head: Pointer?): List<MediaTrack> {
            if (head == null) return emptyList()
            val tracks = mutableListOf<MediaTrack>()
            val seen = mutableSetOf<Long>()
            try {
                var pointer: Pointer? = head
                while (pointer != null && tracks.size < 128 && seen.add(Pointer.nativeValue(pointer))) {
                    val entry = NativeTrackDescription(pointer)
                    tracks += MediaTrack(entry.id, entry.name?.getString(0, "UTF-8")?.take(256) ?: "Faixa ${entry.id}")
                    pointer = entry.next
                }
            } finally { vlc.libvlc_track_description_list_release(head) }
            return tracks.distinctBy { it.id }
        }
        val audio = descriptions(vlc.libvlc_audio_get_track_description(p))
        val subtitles = descriptions(vlc.libvlc_video_get_spu_description(p))
        val audioTrack = vlc.libvlc_audio_get_track(p)
        val subtitleTrack = vlc.libvlc_video_get_spu(p)
        mutable.update { it.copy(audioTracks = audio, subtitleTracks = subtitles,
            audioTrack = audioTrack, subtitleTrack = subtitleTrack) }
    }

    fun selectTrack(id: Int, subtitles: Boolean) = submit(mediaScoped = true) {
        val p = player ?: return@submit
        val tracks = if (subtitles) mutable.value.subtitleTracks else mutable.value.audioTracks
        if (id != -1 && tracks.none { it.id == id }) return@submit
        val result = if (subtitles) api!!.libvlc_video_set_spu(p, id) else api!!.libvlc_audio_set_track(p, id)
        mutable.update { if (result != 0) it.copy(optionError = "Não foi possível selecionar essa faixa.")
            else if (subtitles) it.copy(subtitleTrack = id, optionError = null) else it.copy(audioTrack = id, optionError = null) }
    }

    fun setSpeed(speed: Float) = submit(mediaScoped = true) {
        if (speed !in listOf(.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) return@submit
        val p = player ?: return@submit
        val result = api!!.libvlc_media_player_set_rate(p, speed)
        mutable.update { if (result == 0) it.copy(speed = speed, optionError = null)
            else it.copy(optionError = "Essa transmissão não permite alterar a velocidade.") }
    }

    fun setScaleMode(mode: VideoScaleMode) = submit {
        try {
            applyGeometry(mode)
            mutable.update { it.copy(scaleMode = mode, optionError = null) }
        } catch (_: Exception) { mutable.update { it.copy(optionError = "Não foi possível aplicar o modo de tela.") } }
    }

    fun updateViewport(width: Int, height: Int) = submit {
        viewportWidth = width; viewportHeight = height
        applyGeometry(mutable.value.scaleMode)
    }

    private fun applyGeometry(mode: VideoScaleMode) {
        val p = player ?: return
        val geometry = mode.vlcGeometry(viewportWidth, viewportHeight)
        if (appliedGeometry == geometry) return
        val vlc = api ?: return
        vlc.libvlc_video_set_scale(p, 0f)
        // Clear both old operations so Fit/Fill/Zoom are reversible, including after a resize.
        vlc.libvlc_video_set_aspect_ratio(p, null)
        vlc.libvlc_video_set_crop_geometry(p, null)
        geometry.aspect?.let { vlc.libvlc_video_set_aspect_ratio(p, it) }
        geometry.crop?.let { vlc.libvlc_video_set_crop_geometry(p, it) }
        appliedGeometry = geometry
    }

    private fun startPending() {
        if (surface == null) return
        val request = pendingMedia ?: return
        if (request.generation != generation.get()) { pendingMedia = null; return }
        initialize()
        val vlc = api!!
        val p = player!!
        active = false
        vlc.libvlc_media_player_stop(p)
        // Initialization/stop may block; a newer request must never adopt the old media.
        if (closed.get() || request.generation != generation.get()) { pendingMedia = null; return }
        val media = vlc.libvlc_media_new_location(instance!!, request.address)
            ?: error("Native media unavailable")
        try {
            vlc.libvlc_media_add_option(media, ":network-caching=1200")
            vlc.libvlc_media_add_option(media, ":http-user-agent=IPTVSmarters")
            request.audioLanguage?.let { vlc.libvlc_media_add_option(media, ":audio-language=$it") }
            request.subtitleLanguage?.let { vlc.libvlc_media_add_option(media, ":sub-language=$it") }
            vlc.libvlc_media_player_set_media(p, media)
        } finally { vlc.libvlc_media_release(media) }
        vlc.libvlc_media_player_set_hwnd(p, surface!!)
        vlc.libvlc_audio_set_volume(p, mutable.value.volume)
        vlc.libvlc_audio_set_mute(p, if (mutable.value.muted) 1 else 0)
        appliedGeometry = null
        applyGeometry(mutable.value.scaleMode)
        if (vlc.libvlc_media_player_play(p) != 0) error("Native play failed")
        vlc.libvlc_media_player_set_rate(p, 1f)
        pendingMedia = null
        activeGeneration = request.generation
        active = true
    }

    private fun initialize() {
        if (player != null) return
        val directory = NativeRuntime.directory()
        Kernel32.INSTANCE.SetEnvironmentVariable("VLC_PLUGIN_PATH", directory.resolve("plugins").toString())
        val vlc = Native.load(directory.resolve("libvlc.dll").toString(), LibVlc::class.java,
            mapOf(Library.OPTION_STRING_ENCODING to "UTF-8", Library.OPTION_OPEN_FLAGS to 8))
        api = vlc
        val options = arrayOf("--quiet", "--ignore-config", "--no-video-title-show", "--no-osd", "--no-stats",
            "--http-user-agent=IPTVSmarters")
        val arguments = StringArray(options, "UTF-8")
        val created = vlc.libvlc_new(options.size, arguments) ?: error("Native instance unavailable")
        val createdPlayer = vlc.libvlc_media_player_new(created)
        if (createdPlayer == null) {
            vlc.libvlc_release(created)
            error("Native player unavailable")
        }
        instance = created
        player = createdPlayer
        vlc.libvlc_video_set_key_input(player!!, 0)
        vlc.libvlc_video_set_mouse_input(player!!, 0)
    }

    private fun submit(mediaScoped: Boolean = false, action: () -> Unit) {
        val token = generation.get()
        if (closed.get()) return
        try {
            worker.execute {
                if (!closed.get() && (!mediaScoped || token == generation.get())) guarded(action)
            }
        } catch (_: RejectedExecutionException) {
            // close() can race with a final AWT/Compose event; it owns native cleanup.
        }
    }

    private fun guarded(action: () -> Unit) {
        val token = generation.get()
        try { action() }
        catch (_: Exception) { failed(token) }
        catch (_: LinkageError) { failed(token) }
    }

    private fun failed(token: Long) {
        active = false
        mutable.update { if (closed.get() || token != generation.get()) it else it.copy(phase = "Reprodução indisponível", error =
            "Não foi possível iniciar o vídeo. Confira a conexão ou execute novamente o instalador para verificar as bibliotecas.") }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        generation.incrementAndGet()
        worker.execute {
            pendingMedia = null
            active = false
            try {
                player?.let { api?.libvlc_media_player_stop(it); api?.libvlc_media_player_release(it) }
                instance?.let { api?.libvlc_release(it) }
            } finally { player = null; instance = null; worker.shutdown() }
        }
    }
}

@Structure.FieldOrder("id", "name", "next")
internal class NativeTrackDescription(pointer: Pointer) : Structure(pointer) {
    @JvmField var id: Int = 0
    @JvmField var name: Pointer? = null
    @JvmField var next: Pointer? = null
    init { read() }
}

internal interface LibVlc : Library {
    fun libvlc_audio_get_track_description(player: Pointer): Pointer?
    fun libvlc_video_get_spu_description(player: Pointer): Pointer?
    fun libvlc_track_description_list_release(head: Pointer)
    fun libvlc_audio_get_track(player: Pointer): Int
    fun libvlc_video_get_spu(player: Pointer): Int
    fun libvlc_audio_set_track(player: Pointer, id: Int): Int
    fun libvlc_video_set_spu(player: Pointer, id: Int): Int
    fun libvlc_video_set_aspect_ratio(player: Pointer, aspect: String?)
    fun libvlc_video_set_crop_geometry(player: Pointer, geometry: String?)
    fun libvlc_video_set_scale(player: Pointer, scale: Float)
    fun libvlc_media_player_set_rate(player: Pointer, speed: Float): Int
    fun libvlc_media_player_get_time(player: Pointer): Long
    fun libvlc_media_player_get_length(player: Pointer): Long
    fun libvlc_media_player_is_seekable(player: Pointer): Int
    fun libvlc_media_player_set_time(player: Pointer, time: Long)
    fun libvlc_new(argc: Int, argv: Pointer): Pointer?
    fun libvlc_release(instance: Pointer)
    fun libvlc_media_new_location(instance: Pointer, address: String): Pointer?
    fun libvlc_media_release(media: Pointer)
    fun libvlc_media_add_option(media: Pointer, option: String)
    fun libvlc_media_player_new(instance: Pointer): Pointer?
    fun libvlc_media_player_release(player: Pointer)
    fun libvlc_media_player_set_media(player: Pointer, media: Pointer)
    fun libvlc_media_player_set_hwnd(player: Pointer, hwnd: Pointer)
    fun libvlc_media_player_play(player: Pointer): Int
    fun libvlc_media_player_stop(player: Pointer)
    fun libvlc_media_player_set_pause(player: Pointer, pause: Int)
    fun libvlc_media_player_get_state(player: Pointer): Int
    fun libvlc_audio_set_volume(player: Pointer, volume: Int): Int
    fun libvlc_audio_set_mute(player: Pointer, mute: Int)
    fun libvlc_video_set_key_input(player: Pointer, enabled: Int)
    fun libvlc_video_set_mouse_input(player: Pointer, enabled: Int)
}
