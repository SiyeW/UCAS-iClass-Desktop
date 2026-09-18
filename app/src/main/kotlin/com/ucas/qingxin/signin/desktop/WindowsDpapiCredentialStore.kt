package com.ucas.qingxin.signin.desktop

import com.sun.jna.platform.win32.Crypt32Util
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

data class StoredCredentials(
    val account: String,
    val password: String,
)

interface CredentialStore {
    fun load(): StoredCredentials?

    fun save(credentials: StoredCredentials)

    fun clear()
}

/**
 * Persists encrypted credentials for the current Windows account only.
 *
 * DPAPI normally binds protection to both the current user and this computer;
 * the file is intentionally not portable and is useless without that context.
 */
class WindowsDpapiCredentialStore(
    private val credentialsFile: Path = defaultCredentialsFile(),
) : CredentialStore {
    override fun load(): StoredCredentials? {
        if (!Files.exists(credentialsFile)) return null

        val protectedBytes = Files.readAllBytes(credentialsFile)
        val plainBytes = Crypt32Util.cryptUnprotectData(protectedBytes)
        try {
            val content = JSONObject(String(plainBytes, StandardCharsets.UTF_8))
            val account = content.optString(ACCOUNT_KEY).trim()
            val password = content.optString(PASSWORD_KEY)
            return if (account.isBlank() || password.isBlank()) null else StoredCredentials(account, password)
        } finally {
            protectedBytes.fill(0)
            plainBytes.fill(0)
        }
    }

    override fun save(credentials: StoredCredentials) {
        require(credentials.account.isNotBlank()) { "Account must not be blank." }
        require(credentials.password.isNotBlank()) { "Password must not be blank." }

        val plainBytes = JSONObject()
            .put(VERSION_KEY, FORMAT_VERSION)
            .put(ACCOUNT_KEY, credentials.account.trim())
            .put(PASSWORD_KEY, credentials.password)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        val protectedBytes = Crypt32Util.cryptProtectData(plainBytes)
        try {
            Files.createDirectories(credentialsFile.parent)
            val temporaryFile = Files.createTempFile(credentialsFile.parent, ".credentials-", ".tmp")
            try {
                Files.write(
                    temporaryFile,
                    protectedBytes,
                    StandardOpenOption.TRUNCATE_EXISTING,
                )
                try {
                    Files.move(
                        temporaryFile,
                        credentialsFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporaryFile, credentialsFile, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporaryFile)
            }
        } finally {
            plainBytes.fill(0)
            protectedBytes.fill(0)
        }
    }

    override fun clear() {
        Files.deleteIfExists(credentialsFile)
    }

    companion object {
        private const val FORMAT_VERSION = 1
        private const val VERSION_KEY = "version"
        private const val ACCOUNT_KEY = "account"
        private const val PASSWORD_KEY = "password"

        private fun defaultCredentialsFile(): Path {
            val localAppData = System.getenv("LOCALAPPDATA")
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: Path.of(System.getProperty("user.home"), "AppData", "Local")
            return localAppData.resolve("UCASiClassDesktop").resolve("credentials.dpapi")
        }
    }
}
