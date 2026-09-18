package com.ucas.qingxin.signin.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class WindowsDpapiCredentialStoreTest {
    @Test
    fun roundTripsCredentialsOnlyForTheCurrentWindowsUser() {
        val directory = Files.createTempDirectory("ucas-iclass-dpapi-test")
        val credentialsFile = directory.resolve("credentials.dpapi")
        val store = WindowsDpapiCredentialStore(credentialsFile)

        try {
            store.save(StoredCredentials(account = "2026000000000", password = "test-only-password"))

            assertFalse(Files.readAllBytes(credentialsFile).decodeToString().contains("test-only-password"))
            assertEquals(
                StoredCredentials(account = "2026000000000", password = "test-only-password"),
                store.load(),
            )

            store.clear()
            assertNull(store.load())
        } finally {
            Files.deleteIfExists(credentialsFile)
            Files.deleteIfExists(directory)
        }
    }
}
