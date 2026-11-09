package bd.arena.prototype

import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    private val update=PlayerUpdate("bd.arena.prototype",20,"1.0.8","$UPDATE_REPOSITORY/releases/download/player-v1.0.8/antihack-tour-1.0.8.apk")
    @Test fun acceptsOnlyNewerPlayerVersions() {
        assertTrue(validPlayerUpdate(update,19))
        assertFalse(validPlayerUpdate(update,20))
        assertFalse(validPlayerUpdate(update,21))
        assertFalse(validPlayerUpdate(update.copy(packageId="bd.arena.prototype.admin"),19))
        assertFalse(validPlayerUpdate(update.copy(versionName=""),19))
    }
    @Test fun rejectsDownloadsOutsideReleaseAssets() {
        for(url in listOf("http://github.com/x.apk","https://evil.example/x.apk",
            update.apkUrl.replace("github.com","github.com.evil.example"),
            update.apkUrl.replace("github.com","user@github.com"),
            update.apkUrl.replace("antihack-tour/releases","another-repo/releases"),
            update.apkUrl.replace("antihack-tour-1.0.8.apk","antihack-admin-1.0.8.apk"),
            update.apkUrl+"?redirect=evil",update.apkUrl+"#fragment", "not a URL")) {
            assertFalse(url,validPlayerUpdate(update.copy(apkUrl=url),19))
        }
    }
}

class AppUpdateTransportTest {
    @Test fun acceptsExactSizeAndUtf8Response() {
        val bytes="নতুন আপডেট".toByteArray(Charsets.UTF_8)
        assertEquals("নতুন আপডেট",readUpdateText(bytes.inputStream(),bytes.size))
    }
    @Test(expected=java.io.IOException::class) fun rejectsOversizedUpdateResponse() {
        readUpdateText(ByteArray(8193).inputStream(),8192)
    }
    @Test fun failedCheckRetriesSoonWithBoundedBackoffAndSuccessUsesHourlyCheck() {
        assertEquals(60_000L,updateRetryDelay(1))
        assertEquals(120_000L,updateRetryDelay(2))
        assertEquals(240_000L,updateRetryDelay(3))
        assertEquals(1_800_000L,updateRetryDelay(20))
        assertEquals(3_600_000L,updateRetryDelay(0))
    }
}
