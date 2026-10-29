package rsv.squitv

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rsv.squitv.data.model.*
import rsv.squitv.data.repository.BackupImportResult
import rsv.squitv.data.repository.SettingsBackupRepository
import rsv.squitv.ui.viewmodel.settings.LocalTransferViewModel

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class LocalTransferApprovalTest {
    private lateinit var receiver: LocalTransferViewModel
    private lateinit var sender: LocalTransferViewModel
    private lateinit var receiverBackups: SettingsBackupRepository
    private val advertisedPort = AtomicInteger()
    private val sent = setOf(BackupOption.THEME, BackupOption.COMPACT_MODE)
    private val allowed = setOf(BackupOption.THEME)
    private val backup = SettingsBackup("SquiTV.Settings", 2, 1L,
        BackupPreferences(useOledTheme = false, compactMode = false))

    @Before fun preparePeers() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val nsd = Mockito.mock(NsdManager::class.java)
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.getSystemService(Context.NSD_SERVICE)).thenReturn(nsd)
        Mockito.doAnswer { invocation ->
            advertisedPort.set(invocation.getArgument<NsdServiceInfo>(0).port)
            null
        }.`when`(nsd).registerService(ArgumentMatchers.any(NsdServiceInfo::class.java),
            ArgumentMatchers.eq(NsdManager.PROTOCOL_DNS_SD), ArgumentMatchers.any(NsdManager.RegistrationListener::class.java))
        receiverBackups = Mockito.mock(SettingsBackupRepository::class.java)
        Mockito.`when`(receiverBackups.import(backup, allowed)).thenReturn(BackupImportResult(0, 0))
        val senderBackups = Mockito.mock(SettingsBackupRepository::class.java)
        Mockito.`when`(senderBackups.export(sent)).thenReturn(SettingsBackupCodec.encode(backup))
        receiver = LocalTransferViewModel(context, receiverBackups)
        sender = LocalTransferViewModel(context, senderBackups)
    }

    @After fun closePeers() {
        if (::receiver.isInitialized) receiver.stop()
        if (::sender.isInitialized) sender.stop()
        Dispatchers.resetMain()
    }

    private suspend fun awaitCondition(check: () -> Boolean) {
        withTimeout(8000) { while (!check()) delay(10) }
    }
    private suspend fun requestTransfer() {
        receiver.startReceiver(allowed)
        awaitCondition { advertisedPort.get() > 0 && receiver.state.value.receiving }
        sender.startDiscovery(sent)
        sender.sendManual("127.0.0.1:${advertisedPort.get()}")
        awaitCondition { receiver.state.value.pending != null }
    }

    @Test fun acceptanceAppliesOnlyAllowedItems_andSenderWaitsForAcknowledgement() = runBlocking {
        requestTransfer()
        assertEquals(allowed, receiver.state.value.pending!!.options)
        assertFalse(sender.state.value.completed)
        Mockito.verify(receiverBackups, Mockito.never()).import(backup, allowed)
        receiver.respond(receiver.state.value.pending!!.id, true)
        awaitCondition { receiver.state.value.completed && sender.state.value.completed }
        Mockito.verify(receiverBackups, Mockito.times(1)).import(backup, allowed)
        assertFalse(receiver.state.value.receiving)
        assertEquals(R.string.local_sync_sent, sender.state.value.notice)
    }

    @Test fun rejectionDoesNotApplySettings_andReceiverRemainsAvailable() = runBlocking {
        requestTransfer()
        receiver.respond(receiver.state.value.pending!!.id, false)
        awaitCondition { !sender.state.value.busy && receiver.state.value.pending == null }
        Mockito.verify(receiverBackups, Mockito.never()).import(backup, allowed)
        assertFalse(sender.state.value.completed)
        assertTrue(receiver.state.value.receiving)
        assertEquals(R.string.local_sync_declined, sender.state.value.notice)
    }

    @Test fun closingReceiverBeforeApproval_cancelsRequestWithoutImporting() = runBlocking {
        requestTransfer()
        receiver.stop()
        awaitCondition { !sender.state.value.busy }
        Mockito.verify(receiverBackups, Mockito.never()).import(backup, allowed)
        assertNull(receiver.state.value.pending)
        assertFalse(receiver.state.value.completed)
        assertFalse(sender.state.value.completed)
    }
}
