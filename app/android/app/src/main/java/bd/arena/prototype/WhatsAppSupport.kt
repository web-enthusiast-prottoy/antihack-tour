package bd.arena.prototype

import androidx.compose.runtime.*
import com.google.firebase.firestore.MetadataChanges

internal const val defaultSupportNumber="01703883397"
internal fun supportChatUrl(number:String):String {
    val local=number.trim().removePrefix("+88").removePrefix("88")
    require(Regex("01[3-9][0-9]{8}").matches(local)){"Enter a valid Bangladesh mobile number."}
    return "https://wa.me/88$local"
}
internal data class SupportNumberState(val number:String=defaultSupportNumber,val revision:Long=0,val loading:Boolean=true,val cached:Boolean=true,val error:Boolean=false)

@Composable
internal fun supportNumber(session:AdminSession,refresh:Int=0):SupportNumberState {
    var state by remember(session.uid){mutableStateOf(SupportNumberState())}
    DisposableEffect(session.uid,refresh) {
        val listener=session.db.document("supportSettings/whatsapp").addSnapshotListener(MetadataChanges.INCLUDE){snapshot,error->
            state=if(error!=null)state.copy(loading=false,error=true)else SupportNumberState(snapshot?.getString("number")?:defaultSupportNumber,snapshot?.getLong("revision")?:0,false,snapshot?.metadata?.isFromCache?:true)
        }
        onDispose{listener.remove()}
    }
    return state
}
