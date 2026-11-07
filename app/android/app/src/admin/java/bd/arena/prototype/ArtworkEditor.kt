package bd.arena.prototype

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

internal const val maxArtworkBytes=80000
internal data class ArtworkSelection(val id:String="",val bytes:ByteArray?=null,val preparing:Boolean=false)
internal val artworkSelectionSaver=listSaver<ArtworkSelection,Any>(save={listOf(it.id,it.bytes?:byteArrayOf())},restore={ArtworkSelection(it[0] as String,(it[1] as ByteArray).takeIf{bytes->bytes.isNotEmpty()})})
internal fun artworkRecord(bytes:ByteArray,actor:String)=mapOf("data" to Blob.fromBytes(bytes),"contentType" to "image/webp","createdAt" to FieldValue.serverTimestamp(),"createdBy" to actor)
private fun prepareArtwork(context:Context,uri:Uri):ByteArray {
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    context.contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,bounds)}
    require(bounds.outWidth>0&&bounds.outHeight>0)
    var sample=1
    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1280)sample*=2
    var bitmap=context.contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}?:error("Unreadable image")
    val orientation=runCatching{context.contentResolver.openInputStream(uri).use{android.media.ExifInterface(it!!).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}}.getOrDefault(1)
    if(orientation in 2..8) {
        val matrix=android.graphics.Matrix()
        when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(270f);matrix.postScale(-1f,1f)};8->matrix.setRotate(270f)}
        val oriented=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        if(oriented!==bitmap){bitmap.recycle();bitmap=oriented}
    }
    val scale=640f/maxOf(bitmap.width,bitmap.height)
    if(scale<1){val small=Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).toInt().coerceAtLeast(1),(bitmap.height*scale).toInt().coerceAtLeast(1),true);bitmap.recycle();bitmap=small}
    try {
        var quality=85
        while(true) {
            val output=ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.WEBP,quality,output)
            val bytes=output.toByteArray()
            if(bytes.size<=maxArtworkBytes)return bytes
            if(quality>45)quality-=10
            else {val small=Bitmap.createScaledBitmap(bitmap,(bitmap.width*0.8f).toInt().coerceAtLeast(1),(bitmap.height*0.8f).toInt().coerceAtLeast(1),true);bitmap.recycle();bitmap=small}
        }
    } finally {bitmap.recycle()}
}
@Composable
internal fun ArtworkEditor(session:AdminSession,selection:ArtworkSelection,enabled:Boolean,fallback:Int=R.drawable.home_hero,change:(ArtworkSelection)->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var preparing by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)scope.launch{
        preparing=true;error=false;change(selection.copy(preparing=true))
        val result=withContext(Dispatchers.IO){runCatching{prepareArtwork(context,uri)}}
        preparing=false
        result.onSuccess{change(ArtworkSelection(bytes=it))}.onFailure{error=true;change(selection.copy(preparing=false))}
    }}
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        ArtworkImage(session,selection.id,fallback,Modifier.fillMaxWidth().height(150.dp),selection.bytes)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={picker.launch("image/*")},enabled=enabled&&!preparing){Text(LocaleStrings.get(if(preparing)"Preparing image…" else "Choose image"))}
            if(selection.id.isNotBlank()||selection.bytes!=null)TextButton(onClick={change(ArtworkSelection())},enabled=enabled&&!preparing){Text(LocaleStrings.get("Use default image"))}
        }
        if(error)Text(LocaleStrings.get("Could not read this image. Choose another image."),color=MaterialTheme.colorScheme.error)
    }
}
