package rsv.squitv

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.AEADBadTagException
import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.data.model.SettingsBackupCodec
import rsv.squitv.data.transfer.LocalTransferProtocol as Protocol

class LocalTransferProtocolTest {
    private fun sessions(fingerprint: String? = null): Pair<Protocol.Session, Protocol.Session> {
        val identity = Protocol.identity()
        val executor = Executors.newSingleThreadExecutor()
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).apply { soTimeout = 5000 }
        try {
            val receiver = executor.submit<Protocol.Session> {
                listener.accept().use { socket ->
                    socket.soTimeout = 5000
                    Protocol.receive(DataInputStream(socket.getInputStream()), DataOutputStream(socket.getOutputStream()), identity)
                }
            }
            Socket(InetAddress.getLoopbackAddress(), listener.localPort).use { socket ->
                socket.soTimeout = 5000
                val sender = Protocol.connect(DataInputStream(socket.getInputStream()), DataOutputStream(socket.getOutputStream()),
                    fingerprint ?: Protocol.fingerprint(identity))
                return sender to receiver.get(5, TimeUnit.SECONDS)
            }
        } finally {
            listener.close()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }
    private fun frame(session: Protocol.Session, text: String): ByteArray = ByteArrayOutputStream().also {
        session.write(DataOutputStream(it), text)
    }.toByteArray()
    private fun read(session: Protocol.Session, bytes: ByteArray, limit: Int = SettingsBackupCodec.MAX_BYTES): String =
        session.read(DataInputStream(ByteArrayInputStream(bytes)), limit)

    @Test fun encryptedRoundTrip_usesSeparateDirectionsAndPreservesUnicode() {
        val (sender, receiver) = sessions()
        val text = "Favoritos, séries e organização — ação!"
        val encrypted = frame(sender, text)
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("Favoritos"))
        assertEquals(text, read(receiver, encrypted))
        assertEquals("APPLYING", read(sender, frame(receiver, "APPLYING")))
        assertEquals("OK", read(sender, frame(receiver, "OK")))
    }
    @Test fun discoveredFingerprint_mustMatchReceiver() {
        assertThrows(IllegalArgumentException::class.java) { sessions("0".repeat(64)) }
    }
    @Test fun tamperedPayload_isRejectedBeforeItCanBeApplied() {
        val (sender, receiver) = sessions()
        val encrypted = frame(sender, "settings")
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { read(receiver, encrypted) }
    }
    @Test fun replayedFrame_isRejected() {
        val (sender, receiver) = sessions()
        val encrypted = frame(sender, "once")
        assertEquals("once", read(receiver, encrypted))
        assertThrows(AEADBadTagException::class.java) { read(receiver, encrypted) }
    }
    @Test fun reflectedFrame_cannotBeReadInOppositeDirection() {
        val (sender, _) = sessions()
        val encrypted = frame(sender, "settings")
        assertThrows(AEADBadTagException::class.java) { read(sender, encrypted) }
    }
    @Test fun anotherSession_cannotDecryptPayload() {
        val (sender, _) = sessions()
        val (_, anotherReceiver) = sessions()
        assertThrows(AEADBadTagException::class.java) { read(anotherReceiver, frame(sender, "settings")) }
    }
    @Test fun oversizedOutgoingPayload_isRejected() {
        val (sender, _) = sessions()
        assertThrows(IllegalArgumentException::class.java) { frame(sender, "x".repeat(SettingsBackupCodec.MAX_BYTES + 1)) }
    }
    @Test fun oversizedIncomingFrame_isRejectedBeforeAllocation() {
        val (_, receiver) = sessions()
        val invalid = ByteArrayOutputStream().also {
            DataOutputStream(it).apply { write(ByteArray(12)); writeInt(SettingsBackupCodec.MAX_BYTES + 17) }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { read(receiver, invalid) }
    }
    @Test fun smallControlMessageLimit_isEnforced() {
        val (sender, receiver) = sessions()
        assertThrows(IllegalArgumentException::class.java) { read(receiver, frame(sender, "x".repeat(300)), 256) }
    }
    @Test fun previousCodeBasedProtocol_isExplicitlyRejected() {
        val input = ByteArrayOutputStream().also { DataOutputStream(it).writeUTF("SQUITV-LAN-1") }.toByteArray()
        assertThrows(Protocol.IncompatibleVersion::class.java) {
            Protocol.receive(DataInputStream(ByteArrayInputStream(input)), DataOutputStream(ByteArrayOutputStream()), Protocol.identity())
        }
    }
}
