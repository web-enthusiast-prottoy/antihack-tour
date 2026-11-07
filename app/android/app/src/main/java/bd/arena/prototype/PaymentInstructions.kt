package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.*

internal const val defaultBkashNumber="01889898989"
internal data class PaymentNumberState(val number:String=if(BuildConfig.LOCAL_OPERATIONS)defaultBkashNumber else "",val loading:Boolean=true,val cached:Boolean=true,val error:Boolean=false,val revision:Long=0,val enabled:Boolean=false)

@Composable
internal fun paymentNumber(session:AdminSession):PaymentNumberState {
    var state by remember(session.uid){mutableStateOf(PaymentNumberState())}
    DisposableEffect(session.uid) {
        val stop=session.db.document("paymentInstructions/bkash").addSnapshotListener(MetadataChanges.INCLUDE){snapshot,error->
            state=if(error!=null)PaymentNumberState(loading=false,error=true)else PaymentNumberState(snapshot?.getString("number")?:(if(BuildConfig.LOCAL_OPERATIONS)defaultBkashNumber else ""),false,snapshot?.metadata?.isFromCache?:true,false,snapshot?.getLong("revision")?:0,snapshot?.getBoolean("enabled")?:BuildConfig.LOCAL_OPERATIONS)
        }
        onDispose{stop.remove()}
    }
    return state
}
