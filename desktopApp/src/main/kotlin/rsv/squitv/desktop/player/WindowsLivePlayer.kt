package rsv.squitv.desktop.player

import com.sun.jna.*
import com.sun.jna.platform.win32.Kernel32
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.awt.Canvas
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class MediaTrack(val id: Int, val name: String)

data class PlayerStatus(val phase: String = "Preparando reprodução…", val paused: Boolean = false,
    val volume: Int = 80, val muted: Boolean = false, val error: String? = null,
    val timeMs: Long = 0, val durationMs: Long = 0, val seekable: Boolean = false, val ended: Boolean = false,
    val audioTracks: List<MediaTrack> = emptyList(), val subtitleTracks: List<MediaTrack> = emptyList(),
    val audioTrack: Int = -1, val subtitleTrack: Int = -1, val speed: Float = 1f,
    val aspect: String? = null, val optionError: String? = null, val sourceId: Int? = null)

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
    private var pendingAddress: String? = null
    private var active = false
    private var activeGeneration = 0L

    init {
        worker.scheduleWithFixedDelay({ guarded {
            val p = player ?: return@guarded
            if (!active || activeGeneration != generation.get()) return@guarded
            val polledGeneration = activeGeneration
            val code = api!!.libvlc_media_player_get_state(p)
            mutable.update { if (polledGeneration != generation.get()) it else it.copy(phase = when (code) {
                3 -> if (onDemand) "Reproduzindo" else "Ao vivo"; 4 -> "Pausado"
                6 -> if (onDemand) "Concluído" else "Transmissão encerrada"; 7 -> "Não foi possível reproduzir"
                else -> "Conectando…"
            }, paused = code == 4, error = when (code) {
                6 -> if (onDemand) null else "O servidor encerrou a transmissão. Tente reconectar."
                7 -> if (onDemand) "O conteúdo não respondeu ou o formato está indisponível. Tente novamente."
                    else "O canal não respondeu ou o formato está indisponível. Tente reconectar."
                else -> null
            }, timeMs = if (onDemand) api!!.libvlc_media_player_get_time(p).coerceAtLeast(0) else 0,
                durationMs = if (onDemand) api!!.libvlc_media_player_get_length(p).coerceAtLeast(0) else 0,
                seekable = onDemand && api!!.libvlc_media_player_is_seekable(p) != 0,
                ended = onDemand && code == 6) }
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

    fun play(address: String, sourceId: Int? = null) {
        val token = generation.incrementAndGet()
        mutable.update { it.copy(phase = "Conectando…", paused = false, error = null,
            timeMs = 0, durationMs = 0, seekable = false, ended = false, sourceId = sourceId,
            audioTracks = emptyList(), subtitleTracks = emptyList(), speed = 1f, optionError = null) }
        submit {
            if (token != generation.get()) return@submit
            pendingAddress = address
            startPending()
        }
    }

    fun togglePause() = submit {
        player?.let {
            val paused = !mutable.value.paused
            api!!.libvlc_media_player_set_pause(it, if (paused) 1 else 0)
            mutable.update { current -> current.copy(paused = paused) }
        }
    }

    fun setVolume(volume: Int) = submit {
        val value = volume.coerceIn(0, 100)
        player?.let { api!!.libvlc_audio_set_volume(it, value) }
        mutable.update { it.copy(volume = value) }
    }

    fun toggleMute() = submit {
        val muted = !mutable.value.muted
        player?.let { api!!.libvlc_audio_set_mute(it, if (muted) 1 else 0) }
        mutable.update { it.copy(muted = muted) }
    }

    fun seekTo(timeMs: Long) = submit {
        val status = mutable.value
        if (onDemand && status.seekable && status.durationMs > 0) player?.let {
            val target = timeMs.coerceIn(0, status.durationMs)
            api!!.libvlc_media_player_set_time(it, target)
            mutable.update { current -> current.copy(timeMs = target) }
        }
    }

    fun refreshTracks() = submit {
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
        mutable.update { it.copy(audioTracks = audio, subtitleTracks = subtitles,
            audioTrack = vlc.libvlc_audio_get_track(p), subtitleTrack = vlc.libvlc_video_get_spu(p)) }
    }

    fun selectTrack(id: Int, subtitles: Boolean) = submit {
        val p = player ?: return@submit
        val tracks = if (subtitles) mutable.value.subtitleTracks else mutable.value.audioTracks
        if (id != -1 && tracks.none { it.id == id }) return@submit
        val result = if (subtitles) api!!.libvlc_video_set_spu(p, id) else api!!.libvlc_audio_set_track(p, id)
        mutable.update { if (result != 0) it.copy(optionError = "Não foi possível selecionar essa faixa.")
            else if (subtitles) it.copy(subtitleTrack = id, optionError = null) else it.copy(audioTrack = id, optionError = null) }
    }

    fun setSpeed(speed: Float) = submit {
        if (speed !in listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)) return@submit
        val p = player ?: return@submit
        val result = api!!.libvlc_media_player_set_rate(p, speed)
        mutable.update { if (result == 0) it.copy(speed = speed, optionError = null)
            else it.copy(optionError = "Essa transmissão não permite alterar a velocidade.") }
    }

    fun setAspect(aspect: String?) = submit {
        if (aspect != null && aspect !in listOf("16:9", "4:3")) return@submit
        val p = player ?: return@submit
        api!!.libvlc_video_set_aspect_ratio(p, aspect)
        mutable.update { it.copy(aspect = aspect, optionError = null) }
    }

    private fun startPending() {
        if (surface == null) return
        val address = pendingAddress ?: return
        initialize()
        val vlc = api!!
        val p = player!!
        active = false
        vlc.libvlc_media_player_stop(p)
        val media = vlc.libvlc_media_new_location(instance!!, address)
            ?: error("Native media unavailable")
        try {
            vlc.libvlc_media_add_option(media, ":network-caching=1200")
            vlc.libvlc_media_add_option(media, ":http-user-agent=IPTVSmarters")
            vlc.libvlc_media_player_set_media(p, media)
        } finally { vlc.libvlc_media_release(media) }
        vlc.libvlc_media_player_set_hwnd(p, surface!!)
        vlc.libvlc_audio_set_volume(p, mutable.value.volume)
        vlc.libvlc_audio_set_mute(p, if (mutable.value.muted) 1 else 0)
        vlc.libvlc_video_set_aspect_ratio(p, mutable.value.aspect)
        if (vlc.libvlc_media_player_play(p) != 0) error("Native play failed")
        vlc.libvlc_media_player_set_rate(p, 1f)
        pendingAddress = null
        activeGeneration = generation.get()
        active = true
    }

    private fun initialize() {
        if (player != null) return
        val packaged = System.getProperty("compose.application.resources.dir")?.let { Path.of(it, "vlc") }
        val development = System.getProperty("squitv.vlc.dir")?.let(Path::of)
        val directory = listOfNotNull(packaged, development).firstOrNull { Files.isRegularFile(it.resolve("libvlc.dll")) }
            ?: error("Bundled runtime missing")
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

    private fun submit(action: () -> Unit) {
        if (!closed.get()) worker.execute { guarded(action) }
    }

    private fun guarded(action: () -> Unit) {
        try { action() }
        catch (_: Exception) { failed() }
        catch (_: LinkageError) { failed() }
    }

    private fun failed() {
        active = false
        mutable.update { it.copy(phase = "Reprodução indisponível", error =
            "Não foi possível iniciar o vídeo. Confira a conexão e o runtime incluído no aplicativo.") }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        generation.incrementAndGet()
        worker.execute {
            pendingAddress = null
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
