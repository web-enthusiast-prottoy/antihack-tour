package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.*

@Composable
internal fun CategoriesAdmin(session:AdminSession,back:()->Unit) {
    var retry by remember{mutableStateOf(0)}
    val feed=matchCategories(session,true,retry)
    val rows=feed.rows
    var editing by rememberSaveable{mutableStateOf(false)}
    val editorScroll=rememberLazyListState()
    LaunchedEffect(editing){editorScroll.scrollToItem(0)}
    var id by rememberSaveable{mutableStateOf("")}
    var name by rememberSaveable{mutableStateOf("")}
    var order by rememberSaveable{mutableStateOf("0")}
    var active by rememberSaveable{mutableStateOf(true)}
    var artwork by rememberSaveable(stateSaver=artworkSelectionSaver){mutableStateOf(ArtworkSelection())}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    Column(Modifier.widthIn(max=640.dp).fillMaxSize().imePadding()) {
    Box(Modifier.padding(horizontal=20.dp,vertical=8.dp)){ScreenHeader(LocaleStrings.get("Categories"),back)}
    LazyColumn(Modifier.testTag("category-list").weight(1f).fillMaxWidth(),state=editorScroll,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item{Text(LocaleStrings.get("Organize matches for Home and Explore."),color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(!editing)item{Button(onClick={id="";name="";order="0";active=true;artwork=ArtworkSelection();error="";editing=true}){Text(LocaleStrings.get("Create category"))}}
        if(editing)item {
            Card {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                ArtworkEditor(session,artwork,!busy,if(name.contains("Clash",true))R.drawable.mode_squad else R.drawable.mode_solo){artwork=it}
                OutlinedTextField(name,{name=it},label={Text(LocaleStrings.get("Category name"))},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                OutlinedTextField(order,{order=it},label={Text(LocaleStrings.get("Display order"))},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                Row {Text(LocaleStrings.get("Active"),Modifier.weight(1f));Switch(active,{active=it},enabled=!busy)}
                if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
                Button(onClick={
                    val position=order.toLongOrNull()
                    if(name.trim().isEmpty()||name.trim().length>60||position==null||position !in 0..9999){error="Enter a name and display order from 0 to 9999."}
                    else {
                        busy=true;error=""
                        val ref=if(id.isBlank())session.db.collection("categories").document()else session.db.document("categories/$id")
                        val image=if(artwork.bytes!=null)session.db.collection("artwork").document()else null
                        session.db.runTransaction {batch->
                        batch.get(ref)
                        image?.let{batch.set(it,artworkRecord(artwork.bytes!!,session.uid!!))}
                        batch.set(ref,mapOf("artworkId" to (image?.id?:artwork.id),"name" to name.trim(),"order" to position,"active" to active,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to session.uid!!))
                        batch.set(session.db.collection("auditRecords").document(),mapOf("action" to "update","path" to ref.path,"actor" to session.uid!!,"at" to FieldValue.serverTimestamp()))
                        }.addOnCompleteListener{busy=false;if(it.isSuccessful)editing=false else {if(BuildConfig.DEBUG)android.util.Log.e("AntihackArtwork","Category save failed",it.exception);error="Could not save. Check connection and access."}}
                    }
                },enabled=!busy&&!artwork.preparing){Text(if(busy)"Saving…" else "Save category")}
                if (id.isNotBlank() && !busy) DeleteRecordButton(session, "categories", id, name) { editing=false;retry++ }
                TextButton(onClick={editing=false},enabled=!busy){Text("Cancel")}
            }}
        }
        if(feed.loading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(feed.error)item{Text(LocaleStrings.get("Could not load categories."),color=MaterialTheme.colorScheme.error);TextButton(onClick={retry++}){Text("Retry")}}
        if(!feed.loading&&!feed.error&&rows.isEmpty())item{Text(LocaleStrings.get("Create your first category to organize matches."))}
        if(!editing)items(rows,key={it.id}){row->
            OutlinedCard(onClick={id=row.id;name=row.name;order=row.order.toString();active=row.active;artwork=ArtworkSelection(row.artworkId);editing=true;error=""},modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(row.name,style=MaterialTheme.typography.titleMedium)
                    Text("${if(row.active)"Active" else "Inactive"} · Order ${row.order}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    }
}
