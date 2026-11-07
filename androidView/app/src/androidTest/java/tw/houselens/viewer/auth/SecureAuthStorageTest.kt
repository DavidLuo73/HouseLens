package tw.houselens.viewer.auth

import androidx.test.platform.app.InstrumentationRegistry
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyStore
import java.security.MessageDigest
import java.io.File

class SecureAuthStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val project = "https://storage-test.supabase.co"
    private val session = Json.decodeFromString<UserSession>(
        """{"access_token":"secret-access-test","refresh_token":"secret-refresh-test","expires_in":3600,"token_type":"bearer"}"""
    )
    @Test fun encrypted_session_and_callback_survive_recreation_only_once() = runBlocking {
        val first=SecureAuthStorage(context,project)
        first.erase()
        try {
            first.saveSession(session)
            first.beginLogin()
            first.saveCodeVerifier("secret-verifier-test")
            first.awaitVerifier()
            val second=SecureAuthStorage(context,project)
            assertEquals(session.accessToken,second.loadSession()!!.accessToken)
            assertEquals("secret-verifier-test",second.loadCodeVerifier())
            assertTrue(second.claimCallback())
            assertFalse(SecureAuthStorage(context,project).claimCallback())
            val raw=context.noBackupFilesDir.walkTopDown().filter { it.isFile }
                .joinToString { it.readBytes().toString(Charsets.ISO_8859_1) }
            assertFalse(raw.contains("secret-access-test"))
            assertFalse(raw.contains("secret-refresh-test"))
            assertFalse(raw.contains("secret-verifier-test"))
        } finally { first.erase() }
    }
    @Test fun missing_keystore_key_is_detected_and_reset_does_not_touch_favorites() = runBlocking {
        val store=SecureAuthStorage(context,project)
        store.erase()
        val favorite=File(context.filesDir,"viewer-favorite-sentinel").apply { writeText("saved-id") }
        try {
            store.saveSession(session)
            val suffix=MessageDigest.getInstance("SHA-256").digest(project.toByteArray()).joinToString("") { "%02x".format(it) }
            val keys=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keys.deleteEntry("viewer-auth-$suffix")
            try { SecureAuthStorage(context,project).loadSession(); fail("Missing key must fail closed") } catch (_: AuthStorageException) {}
            store.erase()
            assertNull(SecureAuthStorage(context,project).loadSession())
            assertEquals("saved-id",favorite.readText())
        } finally { store.erase(); favorite.delete() }
    }
    @Test fun deletion_failure_still_destroys_key_and_prevents_ciphertext_restore() = runBlocking {
        val store = SecureAuthStorage(context, project)
        store.erase()
        val suffix = MessageDigest.getInstance("SHA-256").digest(project.toByteArray()).joinToString("") { "%02x".format(it) }
        val alias = "viewer-auth-$suffix"
        val file = File(context.noBackupFilesDir, "$alias.bin")
        val blocker = File(file, "retained-ciphertext")
        try {
            store.saveSession(session)
            val encrypted = file.readBytes()
            assertTrue(file.delete())
            assertTrue(file.mkdir())
            blocker.writeBytes(encrypted)
            val failure = runCatching { store.erase() }.exceptionOrNull()
            assertTrue(failure is AuthStorageException)
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assertFalse("File deletion failure must still destroy the session key", keys.containsAlias(alias))
            assertTrue(blocker.delete())
            assertTrue(file.delete())
            file.writeBytes(encrypted)
            try {
                SecureAuthStorage(context, project).loadSession()
                fail("Retained ciphertext must not restore a signed-out session")
            } catch (_: AuthStorageException) {}
        } finally {
            blocker.delete()
            if (file.isDirectory) file.delete()
            store.erase()
        }
    }
    @Test fun corrupted_ciphertext_is_detected() = runBlocking {
        val store=SecureAuthStorage(context,project)
        store.erase()
        try {
            store.saveSession(session)
            val suffix=MessageDigest.getInstance("SHA-256").digest(project.toByteArray()).joinToString("") { "%02x".format(it) }
            File(context.noBackupFilesDir,"viewer-auth-$suffix.bin").writeBytes(byteArrayOf(1,2,3))
            try { SecureAuthStorage(context,project).loadSession(); fail("Corruption must fail closed") } catch (_: AuthStorageException) {}
        } finally { store.erase() }
    }
}
