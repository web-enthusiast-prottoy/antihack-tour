package bd.arena.prototype

import androidx.compose.runtime.*
import kotlinx.coroutines.launch

internal sealed interface RoomState {
    data object Hidden:RoomState
    data object Loading:RoomState
    data object Waiting:RoomState
    data class Unavailable(val message:String):RoomState
    data class Ready(val roomId:String,val password:String):RoomState
}

internal fun roomResponseState(status:Int,body:String):RoomState = when(status) {
    403,404->RoomState.Waiting
    401->RoomState.Unavailable("Sign in again to check room details.")
    200->runCatching {
        val fields=org.json.JSONObject(body).getJSONObject("fields")
        val enabled=fields.optJSONObject("enabled")?.optBoolean("booleanValue",false)==true
        val id=fields.optJSONObject("roomId")?.optString("stringValue").orEmpty()
        val password=fields.optJSONObject("password")?.optString("stringValue").orEmpty()
        if(!enabled||id.isBlank()||password.isBlank())RoomState.Waiting else RoomState.Ready(id,password)
    }.getOrElse{RoomState.Unavailable("Could not check room details. Reconnect and retry.")}
    else->RoomState.Unavailable("Could not check room details. Reconnect and retry.")
}
internal class RoomAccess(private val session:AdminSession) {
    var state by mutableStateOf<RoomState>(RoomState.Hidden); private set
    private var request:kotlinx.coroutines.Job?=null
    private var generation=0
    fun close() {generation++;request?.cancel();request=null;state=RoomState.Hidden}
    fun open(match:String,refresh:Boolean=false) {
        request?.cancel()
        val version=++generation
        val user=session.auth.currentUser?:run{state=RoomState.Hidden;return}
        if(!refresh)state=RoomState.Loading
        request=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            val next=try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val token=com.google.android.gms.tasks.Tasks.await(user.getIdToken(false),10,java.util.concurrent.TimeUnit.SECONDS).token
                    require(!token.isNullOrBlank())
                    val host=if(BuildConfig.FIREBASE_USE_EMULATOR)"http://10.0.2.2:8080" else "https://firestore.googleapis.com"
                    val id=java.net.URLEncoder.encode(match,"UTF-8")
                    val url=java.net.URL("$host/v1/projects/${BuildConfig.FIREBASE_PROJECT_ID}/databases/(default)/documents/rooms/$id")
                    val connection=url.openConnection() as java.net.HttpURLConnection
                    try {
                        connection.connectTimeout=5000;connection.readTimeout=5000
                        connection.useCaches=false;connection.instanceFollowRedirects=false
                        connection.setRequestProperty("Authorization","Bearer $token")
                        connection.setRequestProperty("Cache-Control","no-store")
                        // This authenticated GET evaluates release time afresh under the same Firestore rules.
                        val status=connection.responseCode
                        roomResponseState(status,if(status==200)connection.inputStream.bufferedReader().use{it.readText()}else "")
                    } finally {connection.disconnect()}
                }
            } catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
            catch(_:Exception){roomResponseState(0,"")}
            if(generation==version&&session.uid==user.uid)state=next
        }
    }
}
