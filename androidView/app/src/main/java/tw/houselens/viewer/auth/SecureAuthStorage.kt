package tw.houselens.viewer.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.user.UserSession
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import tw.houselens.viewer.data.viewerJson

interface ViewerAuthStorage : SessionManager, CodeVerifierCache {
    suspend fun beginLogin()
    suspend fun awaitVerifier()
    suspend fun claimCallback(): Boolean
    suspend fun cancelPending()
    suspend fun erase()
}
class AuthStorageException : Exception("登入資料無法讀取，請重新登入")

@Serializable
private data class AuthRecord(
    val session: UserSession? = null,
    val verifier: String? = null,
    val pending: String? = null,
    val startedAt: Long = 0
)

class SecureAuthStorage(context: Context, projectUrl: String) : ViewerAuthStorage {
    private val suffix = MessageDigest.getInstance("SHA-256").digest(projectUrl.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val alias = "viewer-auth-$suffix"
    private val file = AtomicFile(File(context.noBackupFilesDir, "$alias.bin"))
    private val mutex = Mutex()
    private var verifierReady = CompletableDeferred<Unit>()

    override suspend fun saveSession(session: UserSession) = update { it.copy(session = session) }
    override suspend fun loadSession(): UserSession? = locked { read().session }
    override suspend fun deleteSession() = update { it.copy(session = null) }
    override suspend fun loadCodeVerifier(): String? = locked { read().verifier }
    override suspend fun deleteCodeVerifier() = update { it.copy(verifier = null) }

    override suspend fun beginLogin() {
        locked {
            verifierReady = CompletableDeferred()
            write(read().copy(verifier = null, pending = "preparing", startedAt = System.currentTimeMillis()))
        }
    }
    override suspend fun saveCodeVerifier(codeVerifier: String) {
        try {
            update {
                if (it.pending != "preparing") throw AuthStorageException()
                it.copy(verifier = codeVerifier, pending = "waiting")
            }
            verifierReady.complete(Unit)
        } catch (e: Exception) {
            verifierReady.completeExceptionally(e)
            throw e
        }
    }
    override suspend fun awaitVerifier() { verifierReady.await() }

    override suspend fun claimCallback(): Boolean = locked {
        val record = read()
        if (record.pending != "waiting" || record.verifier.isNullOrEmpty() ||
            System.currentTimeMillis() - record.startedAt !in 0..300_000L) return@locked false
        write(record.copy(pending = "exchanging"))
        true
    }
    override suspend fun cancelPending() {
        update { it.copy(verifier = null, pending = null, startedAt = 0) }
        verifierReady.cancel()
    }
    override suspend fun erase() {
        locked {
            try {
                file.delete()
                if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists() ||
                    File(file.baseFile.path + ".new").exists()) throw AuthStorageException()
            } finally {
                try {
                    keys().deleteEntry(alias)
                } finally {
                    verifierReady.cancel()
                }
            }
        }
    }
    private suspend fun update(change: (AuthRecord) -> AuthRecord) { locked { write(change(read())) } }
    private suspend fun <T> locked(block: () -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) { block() }
    }
    private fun keys() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun read(): AuthRecord {
        try {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return AuthRecord()
            val bytes = file.readFully()
            if (bytes.size !in 29..1_048_576 || bytes[0].toInt() != 12) throw AuthStorageException()
            val key = keys().getKey(alias, null) as? SecretKey ?: throw AuthStorageException()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            val plain = cipher.doFinal(bytes, 13, bytes.size - 13)
            return viewerJson.decodeFromString<AuthRecord>(plain.toString(Charsets.UTF_8))
        } catch (_: Exception) { throw AuthStorageException() }
    }
    private fun write(record: AuthRecord) {
        val key = (keys().getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(viewerJson.encodeToString(record).toByteArray())
        check(cipher.iv.size == 12)
        val output = file.startWrite()
        try {
            output.write(byteArrayOf(12) + cipher.iv + encrypted)
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
    }
}
