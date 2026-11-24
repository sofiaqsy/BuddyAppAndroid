package com.buddy.app.core.network

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

/** El cliente falla rápido con señal mala, igual que iOS (15 s por paso, 30 s en total). */
class NetworkTimeoutsTest {
    @Test fun timeouts_matchIos() {
        val c = OkHttpClient.Builder().withBuddyTimeouts().build()
        assertEquals(15_000, c.connectTimeoutMillis)
        assertEquals(15_000, c.readTimeoutMillis)
        assertEquals(15_000, c.writeTimeoutMillis)
        assertEquals(30_000, c.callTimeoutMillis)
    }
}
