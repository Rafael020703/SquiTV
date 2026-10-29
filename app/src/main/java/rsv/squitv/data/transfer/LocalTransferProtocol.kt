package rsv.squitv.data.transfer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import rsv.squitv.data.model.SettingsBackupCodec

/** Ephemeral ECDH + HKDF-SHA256; each direction has a separate AES-GCM key. */
internal object LocalTransferProtocol {
    const val VERSION = "2"
    private const val MAGIC = "SQUITV-LAN-2"
    private val random = SecureRandom()
    private fun bytes(size: Int) = ByteArray(size).also(random::nextBytes)
    fun identity(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"), random)
        generateKeyPair()
    }
    fun fingerprint(identity: KeyPair): String = fingerprint(identity.public.encoded)
    private fun fingerprint(publicKey: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(publicKey).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    class IncompatibleVersion : Exception()

    fun receive(input: DataInputStream, output: DataOutputStream, identity: KeyPair): Session {
        val version = input.readUTF()
        output.writeUTF(MAGIC)
        output.flush()
        if (version != MAGIC) throw IncompatibleVersion()
        val salt = bytes(32)
        writeKey(output, identity.public.encoded)
        output.write(salt)
        output.flush()
        val senderKey = readKey(input)
        return session(identity, senderKey, salt, identity.public.encoded, senderKey, sending = false)
    }

    fun connect(input: DataInputStream, output: DataOutputStream, expectedFingerprint: String?): Session {
        output.writeUTF(MAGIC)
        output.flush()
        val version = try { input.readUTF() } catch (_: EOFException) { throw IncompatibleVersion() }
        if (version != MAGIC) throw IncompatibleVersion()
        val receiverKey = readKey(input)
        if (expectedFingerprint != null) require(fingerprint(receiverKey) == expectedFingerprint)
        val salt = ByteArray(32).also(input::readFully)
        val identity = identity()
        writeKey(output, identity.public.encoded)
        output.flush()
        return session(identity, receiverKey, salt, receiverKey, identity.public.encoded, sending = true)
    }

    private fun writeKey(output: DataOutputStream, encoded: ByteArray) {
        require(encoded.size in 64..512)
        output.writeShort(encoded.size)
        output.write(encoded)
    }
    private fun readKey(input: DataInputStream): ByteArray {
        val length = input.readUnsignedShort()
        require(length in 64..512)
        return ByteArray(length).also(input::readFully)
    }
    private fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(data)
    }
    private fun session(identity: KeyPair, peer: ByteArray, salt: ByteArray,
        receiver: ByteArray, sender: ByteArray, sending: Boolean): Session {
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peer)) as ECPublicKey
        val ownParams = (identity.public as ECPublicKey).params
        require(publicKey.params.curve == ownParams.curve && publicKey.params.generator == ownParams.generator &&
            publicKey.params.order == ownParams.order && publicKey.params.cofactor == ownParams.cofactor)
        val shared = KeyAgreement.getInstance("ECDH").run {
            init(identity.private)
            doPhase(publicKey, true)
            generateSecret()
        }
        val prk = hmac(salt, shared)
        shared.fill(0)
        val transcript = MAGIC.toByteArray(Charsets.UTF_8) + receiver + sender
        val sendKey = hmac(prk, transcript + "sender".toByteArray(Charsets.UTF_8) + byteArrayOf(1))
        val receiveKey = hmac(prk, transcript + "receiver".toByteArray(Charsets.UTF_8) + byteArrayOf(1))
        prk.fill(0)
        return if (sending) Session(sendKey, receiveKey, "sender", "receiver")
            else Session(receiveKey, sendKey, "receiver", "sender")
    }

    class Session internal constructor(writeKey: ByteArray, readKey: ByteArray,
        private val writeDirection: String, private val readDirection: String) {
        private val writeSecret = SecretKeySpec(writeKey, "AES")
        private val readSecret = SecretKeySpec(readKey, "AES")
        private var written = 0L
        private var read = 0L
        fun write(output: DataOutputStream, text: String) {
            val data = text.toByteArray(Charsets.UTF_8)
            require(data.size <= SettingsBackupCodec.MAX_BYTES)
            val nonce = bytes(12)
            val encrypted = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, writeSecret, GCMParameterSpec(128, nonce))
                updateAAD("$MAGIC:$writeDirection:${written++}".toByteArray(Charsets.UTF_8))
                doFinal(data)
            }
            output.write(nonce)
            output.writeInt(encrypted.size)
            output.write(encrypted)
            output.flush()
        }
        fun read(input: DataInputStream, maxBytes: Int = SettingsBackupCodec.MAX_BYTES): String {
            val nonce = ByteArray(12).also(input::readFully)
            val size = input.readInt()
            require(size in 16..(maxBytes + 16))
            val encrypted = ByteArray(size).also(input::readFully)
            val data = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, readSecret, GCMParameterSpec(128, nonce))
                updateAAD("$MAGIC:$readDirection:${read++}".toByteArray(Charsets.UTF_8))
                doFinal(encrypted)
            }
            return data.toString(Charsets.UTF_8)
        }
    }
}
