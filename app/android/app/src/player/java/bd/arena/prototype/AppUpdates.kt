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
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal const val UPDATE_REPOSITORY = "https://github.com/web-enthusiast-prottoy/antihack-tour"
internal const val UPDATE_MANIFEST_URL = "$UPDATE_REPOSITORY/releases/latest/download/player-update.json"

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

private suspend fun fetchPlayerUpdate():PlayerUpdate? = withContext(Dispatchers.IO) {
    val connection=URL(UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout=10_000
        connection.readTimeout=10_000
        connection.setRequestProperty("User-Agent","AntihackTour/${BuildConfig.VERSION_NAME}")
        if (connection.responseCode != 200) return@withContext null
        // The public release record is small; never consume an unbounded response.
        val bytes=connection.inputStream.use { input ->
            val output=java.io.ByteArrayOutputStream()
            val buffer=ByteArray(1024)
            while (output.size() <= 8192) {
                val count=input.read(buffer)
                if (count == -1) break
                output.write(buffer,0,count)
            }
            require(output.size() <= 8192)
            output.toByteArray()
        }
        parsePlayerUpdate(bytes.toString(Charsets.UTF_8),BuildConfig.VERSION_CODE)
    } finally { connection.disconnect() }
}

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
                while (true) {
                    try { update=fetchPlayerUpdate() }
                    catch (cancelled:CancellationException) { throw cancelled }
                    catch (_:Exception) { /* Retry later without interrupting play. */ }
                    delay(60*60*1000L)
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
