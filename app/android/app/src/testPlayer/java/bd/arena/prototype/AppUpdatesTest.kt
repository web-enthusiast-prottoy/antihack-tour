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
