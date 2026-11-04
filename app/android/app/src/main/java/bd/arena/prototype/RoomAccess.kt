package bd.arena.prototype

import androidx.compose.runtime.*
import com.google.firebase.firestore.*

internal sealed interface RoomState {
    data object Hidden:RoomState
    data object Loading:RoomState
    data class Unavailable(val message:String):RoomState
    data class Ready(val roomId:String,val password:String):RoomState
}
internal class RoomAccess(private val session:AdminSession) {
    var state by mutableStateOf<RoomState>(RoomState.Hidden); private set
    private var registration:ListenerRegistration?=null
    private var generation=0
    fun close() {generation++;registration?.remove();registration=null;state=RoomState.Hidden}
    fun open(match:String) {
        close()
        val uid=session.uid?:return
        val version=generation
        state=RoomState.Loading
        registration=session.db.document("rooms/$match").addSnapshotListener(MetadataChanges.INCLUDE) {s,e ->
            if(generation==version && session.uid==uid) {
                state=when {
                    e!=null -> RoomState.Unavailable("Room unavailable. Check the release time and your confirmed entry, then retry.")
                    s==null || s.metadata.isFromCache || s.metadata.hasPendingWrites() -> RoomState.Unavailable("Connect to the internet to view current room details.")
                    !s.exists() -> RoomState.Unavailable("Room unavailable. Check the release time and your confirmed entry, then retry.")
                    else -> RoomState.Ready(s.getString("roomId").orEmpty(),s.getString("password").orEmpty())
                }
            }
        }
    }
}
