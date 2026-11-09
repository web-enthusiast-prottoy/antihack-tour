package bd.arena.prototype

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AppUpdateFetchTest {
    private val manifest="""{"packageId":"bd.arena.prototype","versionCode":21,"versionName":"1.0.9","apkUrl":"https://github.com/web-enthusiast-prottoy/antihack-tour/releases/download/player-v1.0.9/antihack-tour-1.0.9.apk"}"""
    private fun release(embedded:Boolean=true,tag:String="player-v1.0.9",draft:Boolean=false)=JSONObject()
        .put("tag_name",tag).put("draft",draft).put("prerelease",false)
        .put("body",if(embedded)"Release notes\n$UPDATE_RECORD_MARKER\n$manifest\n-->" else "Release notes")
        .put("assets",JSONArray().put(JSONObject().put("id",123).put("name","player-update.json").put("size",293).put("state","uploaded"))).toString()
    @Test fun embeddedApiRecordWorksWhenTheDownloadGatewayIsUnavailable() {
        val urls=mutableListOf<String>()
        val update=loadPlayerUpdate(20){url,_,_->
            urls+=url
            if(url=="$UPDATE_API_ROOT/latest")release()else throw IOException("504 gateway timeout")
        }
        assertEquals(21,update!!.versionCode)
        assertEquals(listOf("$UPDATE_API_ROOT/latest"),urls)
    }
    @Test fun olderReleaseUsesBoundedPublicApiAssetWithoutFollowingUntrustedUrls() {
        val calls=mutableListOf<Pair<String,String>>()
        assertNotNull(loadPlayerUpdate(20){url,limit,accept->
            calls+=url to accept
            when(url){"$UPDATE_API_ROOT/latest"->release(false);"$UPDATE_API_ROOT/assets/123"->{assertEquals(8192,limit);manifest};else->throw IOException()}
        })
        assertEquals(listOf("$UPDATE_API_ROOT/latest" to "application/vnd.github+json","$UPDATE_API_ROOT/assets/123" to "application/octet-stream"),calls)
    }
    @Test fun apiOutageFallsBackToOriginalManifestAndKeepsCurrentVersionQuiet() {
        val read:(String,Int,String)->String={url,_,_->if(url==UPDATE_MANIFEST_URL)manifest else throw IOException("API unavailable")}
        assertNotNull(loadPlayerUpdate(20,read))
        assertNull(loadPlayerUpdate(21,read))
        assertNull(loadPlayerUpdate(22,read))
    }
    @Test(expected=IOException::class) fun bothRoutesFailInsteadOfReportingUpToDate() {
        loadPlayerUpdate(20){_,_,_->throw IOException("offline")}
    }
    @Test(expected=IOException::class) fun draftReleaseCannotTriggerAnAlert() {
        loadPlayerUpdate(20){url,_,_->if(url=="$UPDATE_API_ROOT/latest")release(draft=true)else throw IOException("no fallback")}
    }
    @Test(expected=IOException::class) fun mismatchedReleaseTagCannotTriggerAnAlert() {
        loadPlayerUpdate(20){url,_,_->if(url=="$UPDATE_API_ROOT/latest")release(tag="player-v2.0.0")else throw IOException("no fallback")}
    }
}
