package rsv.squitv.core.data.repository

import android.util.Base64
import rsv.squitv.data.api.XtreamService
import rsv.squitv.data.local.dao.IptvDao
import rsv.squitv.data.local.entities.EpgProgramEntity
import rsv.squitv.data.local.entities.EpgProgramStagingEntity
import rsv.squitv.data.local.entities.EpgReminderEntity
import rsv.squitv.data.model.EpgProgramme
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.data.model.XtreamShortEpg
import rsv.squitv.data.network.EpgService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgRepository @Inject constructor(
    private val xtreamService: XtreamService,
    private val epgService: EpgService,
    private val iptvDao: IptvDao
) {
    private val epgMutex = Mutex()

    suspend fun syncFullEpg(creds: XtreamCredentials, beforePublish: suspend () -> Unit = {}): Int = withContext(Dispatchers.IO) {
        epgMutex.withLock {
            try {
                iptvDao.clearEpgStaging()
                val url = "${creds.baseUrl}/xmltv.php?username=${creds.username}&password=${creds.password}"
                epgService.getEpg(url).use { responseBody ->
                    val inputStream = responseBody.byteStream()

                    val factory = XmlPullParserFactory.newInstance()
                    factory.isNamespaceAware = true
                    val parser = factory.newPullParser()
                    parser.setInput(inputStream, "UTF-8")

                    val sdf = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
                    val batchSize = 1000
                    val stagingEntities = mutableListOf<EpgProgramStagingEntity>()
                    val currentTime = System.currentTimeMillis() / 1000
                    var programCount = 0
                    var hasTvRoot = false

                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        currentCoroutineContext().ensureActive()
                        if (eventType == XmlPullParser.START_TAG && parser.depth == 1) {
                            require(parser.name == "tv") { "Resposta inválida do guia de programação" }
                            hasTvRoot = true
                        }

                        if (eventType == XmlPullParser.START_TAG && parser.name == "programme") {
                            val channelId = parser.getAttributeValue(null, "channel")
                            val startStr = parser.getAttributeValue(null, "start")
                            val stopStr = parser.getAttributeValue(null, "stop")

                            var title: String? = null
                            var desc: String? = null
                            var category: String? = null

                            eventType = parser.next()
                            while (!(eventType == XmlPullParser.END_TAG && parser.name == "programme")) {
                                if (eventType == XmlPullParser.START_TAG) {
                                    when (parser.name) {
                                        "title" -> title = parser.nextText()
                                        "desc" -> desc = parser.nextText()
                                        "category" -> category = parser.nextText()
                                    }
                                }
                                eventType = parser.next()
                            }

                            if (channelId != null && startStr != null && stopStr != null) {
                                try {
                                    val start = sdf.parse(startStr)?.time?.div(1000)
                                    val stop = sdf.parse(stopStr)?.time?.div(1000)

                                    if (start != null && stop != null && stop > currentTime) {
                                        stagingEntities.add(EpgProgramStagingEntity(
                                            channelId = channelId,
                                            startTime = start,
                                            stopTime = stop,
                                            title = title ?: "Sem título",
                                            description = desc,
                                            category = category
                                        ))
                                        programCount++
                                    }
                                } catch (_: Exception) {}
                            }

                            if (stagingEntities.size >= batchSize) {
                                iptvDao.insertEpgStaging(stagingEntities.toList())
                                stagingEntities.clear()
                            }
                        }
                        eventType = parser.next()
                    }

                    if (stagingEntities.isNotEmpty()) {
                        iptvDao.insertEpgStaging(stagingEntities)
                    }

                    // Final step: Atomic publish
                    require(hasTvRoot && programCount > 0) { "O guia recebido não contém programação válida. O guia anterior foi preservado." }
                    currentCoroutineContext().ensureActive()
                    beforePublish()
                    iptvDao.publishStagingEpg()
                    Timber.i("EPG sincronizado com sucesso e publicado atomicamente")
                    programCount
                }

            } catch (e: Exception) {
                withContext(NonCancellable) { iptvDao.clearEpgStaging() }
                if (e !is CancellationException) Timber.e(e, "Erro ao sincronizar EPG total")
                throw e
            }
        }
    }

    fun getEpgForChannel(channelId: String): Flow<List<EpgProgramEntity>> =
        iptvDao.getEpgForChannel(channelId, System.currentTimeMillis() / 1000)

    suspend fun getEpgInRange(startTime: Long, stopTime: Long): List<EpgProgramEntity> =
        iptvDao.getEpgInRange(startTime, stopTime)

    suspend fun getShortEpg(credentials: XtreamCredentials, streamId: Int): XtreamShortEpg {
        val response = xtreamService.getShortEpg(credentials.username, credentials.password, streamId = streamId)
        return response.copy(
            epgListings = response.epgListings?.map { listing ->
                listing.copy(
                    title = decodeBase64IfEncoded(listing.title),
                    description = decodeBase64IfEncoded(listing.description)
                )
            }
        )
    }

    private fun decodeBase64IfEncoded(text: String?): String? {
        if (text.isNullOrBlank()) return text
        if (text.contains(" ") || text.length < 4) return text

        // Simple heuristic to check if it looks like Base64
        val base64Regex = Regex("^[a-zA-Z0-9+/]*={0,2}$")
        if (!base64Regex.matches(text)) return text

        return try {
            val decodedBytes = Base64.decode(text, Base64.DEFAULT)
            val decodedText = String(decodedBytes, Charsets.UTF_8)

            // Validate if decoded text is readable
            val isReadable = decodedText.all {
                it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?-:;()\"\'&@#%^*"
            }
            if (isReadable) decodedText else text
        } catch (e: Exception) {
            Timber.v("Falha ao decodificar Base64: $text")
            text
        }
    }

    suspend fun insertReminder(reminder: EpgReminderEntity) = iptvDao.insertReminder(reminder)

    fun getAllRemindersFlow(): Flow<List<EpgReminderEntity>> = iptvDao.getAllRemindersFlow()

    suspend fun deleteReminder(streamId: Int, startTime: Long) = iptvDao.deleteReminder(streamId, startTime)

    suspend fun hasReminder(streamId: Int, startTime: Long): Boolean = iptvDao.hasReminder(streamId, startTime)

    suspend fun clearAllEpg() = epgMutex.withLock { iptvDao.clearAllEpg() }
}
