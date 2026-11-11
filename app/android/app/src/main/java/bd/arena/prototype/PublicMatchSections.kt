package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

internal data class PublicPrize(val rank:Long,val paisa:Long)

@Composable
internal fun PublicPrizeContent(rows:List<PublicPrize>,enabled:Boolean,loading:Boolean,cached:Boolean,failed:Boolean,perKill:Long?,retry:()->Unit) {
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(LocaleStrings.get("Prize distribution"),style=MaterialTheme.typography.titleMedium)
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth().testTag("prizes-loading"))
            if(cached)Text(LocaleStrings.get("Saved prizes · reconnect to confirm"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(enabled) {
                LabeledValue(LocaleStrings.get("Position"),LocaleStrings.get("Amount"))
                rows.filter{it.paisa>0}.sortedBy{it.rank}.forEach{row->
                    Surface(color=if(row.rank==1L)MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Text("#${row.rank}",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,color=if(row.rank==1L)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(money(row.paisa).removeSuffix(".00"),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                if(!loading&&!failed&&rows.none{it.paisa>0})Text(LocaleStrings.get("No placement prizes."))
            } else if(!loading&&!failed)Text(LocaleStrings.get("Prize breakdown not published."),color=MaterialTheme.colorScheme.onSurfaceVariant)
            perKill?.let{LabeledValue(LocaleStrings.get("Per kill"),money(it).removeSuffix(".00"))}
            if(failed) {
                Text(LocaleStrings.get("Could not load prizes."),color=MaterialTheme.colorScheme.error)
                OutlinedButton(onClick=retry,modifier=Modifier.testTag("prizes-retry")){Text(LocaleStrings.get("Retry"))}
            }
        }
    }
}

internal data class PublicParticipant(val id:String,val name:String,val roster:List<String>,val ffUid:String="")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PublicParticipantContent(rows:List<PublicParticipant>,loading:Boolean,cached:Boolean,more:Boolean,error:Boolean,refresh:()->Unit,loadMore:()->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(LocaleStrings.get("Participants"),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            if(rows.isNotEmpty())Text("${rows.size}"+(if(more||cached)" · "+LocaleStrings.get("Loaded") else ""),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick=refresh,enabled=!loading,modifier=Modifier.testTag("participants-refresh")) {
                Icon(painterResource(R.drawable.ic_refresh),LocaleStrings.get(if(error)"Retry" else "Refresh"))
            }
        }
        if(cached)Text(LocaleStrings.get("Offline"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEachIndexed { index,row ->
            Surface(color=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().testTag("participant-${row.id}")) {
                Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text((index+1).toString().padStart(2,'0'),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(row.name,style=MaterialTheme.typography.titleSmall)
                        if(row.ffUid.isNotBlank())Text("FF UID · "+row.ffUid,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(row.roster.size>1)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            row.roster.drop(1).forEach{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }
                    }
                }
            }
        }
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth().testTag("participants-loading"))
        if(error)Text(LocaleStrings.get("Could not load participants. Reconnect and retry."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
        if(!loading&&!error&&rows.isEmpty())Text(LocaleStrings.get("No participants yet."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(more)OutlinedButton(onClick=loadMore,enabled=!loading,modifier=Modifier.fillMaxWidth().testTag("participants-more")){Text(LocaleStrings.get("Load more"))}
    }
}
