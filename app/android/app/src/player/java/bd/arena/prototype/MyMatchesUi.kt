package bd.arena.prototype

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun MyMatchCard(session:AdminSession,match:Tournament,entry:String?,now:Long,open:()->Unit,room:()->Unit) {
    MyMatchCard(match,entry,now,open,room){m->ArtworkImage(session,match.artworkId,modeArtwork(match.image),m)}
}

@Composable
internal fun MyMatchCard(match:Tournament,entry:String?,now:Long,open:()->Unit,room:()->Unit,artwork:@Composable (Modifier)->Unit) {
    Card(Modifier.fillMaxWidth().testTag("my-match-${match.id}"),shape=RoundedCornerShape(20.dp),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
        Surface(onClick=open,color=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface,modifier=Modifier.testTag("match-card-${match.id}")) {
            Column {
                Box(Modifier.fillMaxWidth().heightIn(min=108.dp)) {
                    artwork(Modifier.matchParentSize())
                    Surface(color=MaterialTheme.colorScheme.background.copy(alpha=.96f),contentColor=MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(10.dp),modifier=Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                        Row(Modifier.padding(horizontal=10.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            Icon(painterResource(R.drawable.ic_clock),null,Modifier.size(14.dp),tint=MaterialTheme.colorScheme.primary)
                            Text(exploreStartText(match,now),style=MaterialTheme.typography.labelSmall.copy(fontFeatureSettings="tnum"))
                        }
                    }
                }
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(match.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        Icon(painterResource(R.drawable.ic_chevron),null,Modifier.size(18.dp))
                    }
                    Text(listOf(LocaleStrings.get(match.mode.lowercase()).replaceFirstChar{it.titlecase()},match.map,match.time).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        @Composable fun Status(modifier:Modifier) {
            Text(LocaleStrings.get(if(match.matchStatus=="cancelled")"Cancelled" else entry.orEmpty()).replaceFirstChar{it.titlecase()},modifier,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        @Composable fun Room(modifier:Modifier) {
            Button(onClick=room,enabled=entry=="confirmed"&&match.matchStatus!="cancelled",modifier=modifier.heightIn(min=48.dp).testTag("my-room-${match.id}")){Text(LocaleStrings.get("View room"))}
        }
        val footer=Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,bottom=12.dp)
        if(LocalDensity.current.fontScale>1.3f)Column(footer,verticalArrangement=Arrangement.spacedBy(12.dp)){Status(Modifier);Room(Modifier.fillMaxWidth())}
        else Row(footer,horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){Status(Modifier.weight(1f));Room(Modifier)}
    }
}

@Composable
internal fun MyMatchRoom(session:AdminSession,match:String,entry:String?,bangla:Boolean,title:String="") {
    var retry by remember(match){mutableStateOf(0)}
    val status=playerBanStatus(session,"matches/$match/bans/${session.uid}",retry)
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(title.isNotBlank())Text(title,style=MaterialTheme.typography.titleMedium)
        if(!status.ready||status.active)MatchBanNotice(status,bangla){retry++}
        else MatchRoomCard(session,match,entry=="confirmed",entry)
    }
}
