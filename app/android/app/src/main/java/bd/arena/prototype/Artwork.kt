package bd.arena.prototype

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ArtworkImage(session:AdminSession,id:String,fallback:Int,modifier:Modifier=Modifier,preview:ByteArray?=null) {
    var bytes by remember(id){mutableStateOf<ByteArray?>(null)}
    DisposableEffect(id) {
        var active=true
        if(id.isNotBlank()) {
            val ref=session.db.document("artwork/$id")
            fun server(){ref.get(com.google.firebase.firestore.Source.SERVER).addOnSuccessListener{if(active)bytes=it.getBlob("data")?.toBytes()}}
            ref.get(com.google.firebase.firestore.Source.CACHE).addOnSuccessListener{cached->if(active){if(cached.exists())bytes=cached.getBlob("data")?.toBytes()else server()}}.addOnFailureListener{if(active)server()}
        }
        onDispose{active=false}
    }
    val data=preview?:bytes
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,data) {
        value=withContext(Dispatchers.Default){data?.let{runCatching{BitmapFactory.decodeByteArray(it,0,it.size)?.asImageBitmap()}.getOrNull()}}
    }
    if(bitmap!=null)Image(bitmap!!,null,modifier,contentScale=ContentScale.Crop)
    else Image(painterResource(fallback),null,modifier,contentScale=ContentScale.Crop)
}
