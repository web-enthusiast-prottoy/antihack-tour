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

internal data class MatchCategory(val id:String,val name:String,val order:Long,val active:Boolean,val artworkId:String="")
internal data class CategoryFeed(val rows:List<MatchCategory> = emptyList(),val loading:Boolean=true,val error:Boolean=false,val cached:Boolean=false)
@Composable
internal fun matchCategories(session:AdminSession,staff:Boolean=false,retry:Int=0):CategoryFeed {
    var feed by remember(session.uid,staff){mutableStateOf(CategoryFeed())}
    DisposableEffect(session.uid,staff,retry) {
        var query:Query=session.db.collection("categories")
        if(!staff)query=query.whereEqualTo("active",true)
        feed=feed.copy(loading=true,error=false)
        val listener=BoundedFeed(query,automatic=true) {state->
            feed=CategoryFeed(state.rows.map{MatchCategory(it.id,it.getString("name").orEmpty(),it.getLong("order")?:0,it.getBoolean("active")==true,it.getString("artworkId").orEmpty())}.sortedWith(compareBy<MatchCategory>{it.order}.thenBy{it.name}),state.loading,state.error,state.cached)
        }
        listener.start()
        onDispose{listener.close()}
    }
    return feed
}
internal fun LazyListScope.categoryTiles(session:AdminSession,categories:List<MatchCategory>,selected:String,showAll:Boolean=false,choose:(String)->Unit) {
    if(showAll) item {TextButton(onClick={choose("")}){Text(LocaleStrings.get("All"))}}
    items(categories.chunked(2),key={it.first().id}){pair->CategoryTileRow(session,pair,selected,choose)}
}
@Composable
private fun CategoryTileRow(session:AdminSession,pair:List<MatchCategory>,selected:String,choose:(String)->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        pair.forEach{category->Card(onClick={choose(category.id)},modifier=Modifier.weight(1f),border=if(selected==category.id)androidx.compose.foundation.BorderStroke(2.dp,MaterialTheme.colorScheme.primary)else null) {
            Box {
                ArtworkImage(session,category.artworkId,if(category.name.contains("Clash",true))R.drawable.mode_squad else R.drawable.mode_solo,Modifier.fillMaxWidth().aspectRatio(1f))
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=0.9f)))))
                Text(category.name,Modifier.align(Alignment.BottomStart).padding(14.dp),style=MaterialTheme.typography.titleMedium,color=Color.White)
            }
        }}
        if(pair.size==1)Spacer(Modifier.weight(1f))
    }
}
