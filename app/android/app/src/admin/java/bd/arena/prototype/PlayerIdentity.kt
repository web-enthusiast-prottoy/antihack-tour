package bd.arena.prototype

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.Source

@Composable
internal fun PlayerIdentity(session:AdminSession,uid:String,name:String="") {
    var profile by remember(uid){mutableStateOf<Map<String,Any>?>(null)}
    var failed by remember(uid){mutableStateOf(false)}
    DisposableEffect(uid){var active=true
        session.db.document("users/$uid").get(Source.SERVER).addOnCompleteListener{if(active){failed=!it.isSuccessful;profile=if(it.isSuccessful)it.result.data else null}}
        onDispose{active=false}
    }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if(name.isNotBlank())Text(name,style=MaterialTheme.typography.titleMedium)
        profile?.get("email")?.toString()?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodyMedium)}
        profile?.get("ffUid")?.toString()?.takeIf{it.isNotBlank()}?.let{Text("Free Fire UID: $it",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(profile!=null&&profile?.get("email")==null)Text("Email available after this player signs in with the updated app.",style=MaterialTheme.typography.bodySmall)
        if(failed)Text(adminText("Could not load the record. Reconnect and retry."),style=MaterialTheme.typography.bodySmall)
    }
}
