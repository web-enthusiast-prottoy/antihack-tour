package bd.arena.prototype

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal const val UPDATE_REPOSITORY = "https://github.com/web-enthusiast-prottoy/antihack-tour"
internal const val UPDATE_MANIFEST_URL = "$UPDATE_REPOSITORY/releases/latest/download/player-update.json"

internal const val UPDATE_API_ROOT = "https://api.github.com/repos/web-enthusiast-prottoy/antihack-tour/releases"
internal const val UPDATE_RECORD_MARKER = "<!-- antihack-player-update"

internal data class PlayerUpdate(val packageId:String, val versionCode:Int, val versionName:String, val apkUrl:String)

internal fun validPlayerUpdate(update:PlayerUpdate, installedVersion:Int):Boolean {
    if (update.packageId != "bd.arena.prototype" || update.versionCode <= installedVersion ||
        !update.versionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) return false
    return runCatching {
        val uri=URI(update.apkUrl)
        uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 &&
            uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath.matches(Regex("/web-enthusiast-prottoy/antihack-tour/releases/download/[A-Za-z0-9._-]+/antihack-tour-[0-9.]+\\.apk"))
    }.getOrDefault(false)
}

internal fun parsePlayerUpdate(json:String, installedVersion:Int):PlayerUpdate? {
    val record=JSONObject(json)
    val update=PlayerUpdate(record.getString("packageId"),record.getInt("versionCode"),record.getString("versionName"),record.getString("apkUrl"))
    return update.takeIf { validPlayerUpdate(it,installedVersion) }
}

internal fun readUpdateText(input:InputStream,limit:Int):String {
    val output=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(1024)
    while(true) {
        val count=input.read(buffer)
        if(count==-1)break
        if(output.size()+count>limit)throw IOException("Update response too large")
        output.write(buffer,0,count)
    }
    return output.toString("UTF-8")
}

private fun readUpdateResponse(url:String,limit:Int,accept:String):String {
    val connection=URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout=10_000
        connection.readTimeout=10_000
        connection.useCaches=false
        connection.setRequestProperty("User-Agent","AntihackTour/${BuildConfig.VERSION_NAME}")
        connection.setRequestProperty("Accept",accept)
        if(connection.responseCode!=200)throw IOException("Update check failed: ${connection.responseCode}")
        return connection.inputStream.use{readUpdateText(it,limit)}
    } finally {connection.disconnect()}
}

/** Public API first; the download endpoint remains a fallback for API outages/rate limits. */
internal fun loadPlayerUpdate(installedVersion:Int,read:(String,Int,String)->String=::readUpdateResponse):PlayerUpdate? {
    try {
        val release=JSONObject(read("$UPDATE_API_ROOT/latest",128*1024,"application/vnd.github+json"))
        require(!release.getBoolean("draft")&&!release.getBoolean("prerelease"))
        val tag=release.getString("tag_name")
        require(tag.matches(Regex("player-v[0-9]+\\.[0-9]+\\.[0-9]+")))
        val body=release.optString("body")
        val embedded=body.substringAfter(UPDATE_RECORD_MARKER,"").substringBefore("-->").trim()
        val json=if(embedded.isNotEmpty())embedded else {
            // Older releases have only the manifest asset; never follow an API URL supplied in JSON.
            val assets=release.getJSONArray("assets")
            val asset=(0 until assets.length()).map{assets.getJSONObject(it)}.single{it.getString("name")=="player-update.json"}
            val id=asset.getLong("id")
            require(id>0&&asset.getString("state")=="uploaded"&&asset.getLong("size") in 1..8192)
            read("$UPDATE_API_ROOT/assets/$id",8192,"application/octet-stream")
        }
        val record=JSONObject(json)
        val version=tag.removePrefix("player-v")
        require(record.getString("versionName")==version&&record.getString("apkUrl")=="$UPDATE_REPOSITORY/releases/download/$tag/antihack-tour-$version.apk")
        return parsePlayerUpdate(json,installedVersion)
    } catch (_:Exception) {
        // A failure must reach the retry loop if neither independent route can be read.
        return parsePlayerUpdate(read(UPDATE_MANIFEST_URL,8192,"application/json"),installedVersion)
    }
}

internal fun updateRetryDelay(failures:Int):Long = if(failures==0)60*60*1000L else
    (60_000L*(1L shl (failures-1).coerceIn(0,5))).coerceAtMost(30*60*1000L)

internal suspend fun fetchPlayerUpdate(installedVersion:Int=BuildConfig.VERSION_CODE):PlayerUpdate? =
    withContext(Dispatchers.IO){loadPlayerUpdate(installedVersion)}

/** Checks only the live player app. Offline/missing releases never prevent using the app. */
@Composable
internal fun PlayerUpdateNotice(bangla:Boolean) {
    if (BuildConfig.FIREBASE_USE_EMULATOR) return
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val scope=rememberCoroutineScope()
    var update by remember { mutableStateOf<PlayerUpdate?>(null) }
    var dismissedVersion by rememberSaveable { mutableStateOf(0) }
    var downloadError by remember { mutableStateOf(false) }
    DisposableEffect(owner) {
        var job:Job?=null
        fun start() {
            if (job?.isActive == true) return
            job=scope.launch {
                var failures=0
                while (true) {
                    try { update=fetchPlayerUpdate();failures=0 }
                    catch (cancelled:CancellationException) { throw cancelled }
                    catch (_:Exception) { failures++ }
                    delay(updateRetryDelay(failures))
                }
            }
        }
        val observer=LifecycleEventObserver { _,event ->
            when(event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> { job?.cancel();job=null }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose { owner.lifecycle.removeObserver(observer);job?.cancel() }
    }
    val available=update?.takeIf { it.versionCode != dismissedVersion } ?: return
    PlayerUpdateDialog(available,bangla,downloadError,onLater={dismissedVersion=available.versionCode},onUpdate={
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(available.apkUrl)))
            downloadError=false
        } catch (_:ActivityNotFoundException) { downloadError=true }
    })
}

@Composable
internal fun PlayerUpdateDialog(update:PlayerUpdate,bangla:Boolean,downloadError:Boolean,onLater:()->Unit,onUpdate:()->Unit) {
    AlertDialog(onDismissRequest=onLater,
        title={Text(if(bangla) "নতুন আপডেট এসেছে" else "Update available")},
        text={Text(if(downloadError) {
            if(bangla) "ডাউনলোড খুলতে একটি ব্রাউজার প্রয়োজন। ব্রাউজার চালু করে আবার চেষ্টা করুন।" else "A browser is needed to open the download. Enable a browser and try again."
        } else if(bangla) "Antihack Tour ${update.versionName} ডাউনলোড করুন। ডাউনলোড শেষে APK ফাইল খুলে Android-এ আপডেটের অনুমতি দিন। বর্তমান অ্যাপ আনইনস্টল করবেন না।"
        else "Download Antihack Tour ${update.versionName}. Then open the APK and approve the Android update. Keep your current app installed.")},
        confirmButton={TextButton(onClick=onUpdate) {Text(if(bangla) "আপডেট ডাউনলোড করুন" else "Download update")}},
        dismissButton={TextButton(onClick=onLater) {Text(if(bangla) "পরে" else "Later")}})
}
