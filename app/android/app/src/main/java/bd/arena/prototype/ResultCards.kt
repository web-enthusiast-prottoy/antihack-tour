package bd.arena.prototype

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

internal fun compactMoney(paisa:Long)=money(paisa).removeSuffix(".00")
internal data class ResultSummary(val id:String,val match:String,val title:String,val mode:String,val map:String,val time:String,val username:String,
    val placement:Long,val kills:Long,val award:Long,val credited:Long,val settlement:String,val version:Long,val placementReward:Long,val killReward:Long,val killsFinal:Boolean)

@Composable
internal fun MatchSectionTabs(results:Boolean,upcoming:()->Unit,history:()->Unit) {
    if(LocalDensity.current.fontScale>1.3f)Column {
        FilterChip(!results,upcoming,label={Text(LocaleStrings.get("Upcoming"))},modifier=Modifier.testTag("my-upcoming"))
        FilterChip(results,history,label={Text(LocaleStrings.get("Results"))},modifier=Modifier.testTag("my-results"))
    } else TabRow(if(results)1 else 0,containerColor=MaterialTheme.colorScheme.background) {
        listOf("Upcoming","Results").forEachIndexed{i,text->Tab((i==1)==results,onClick=if(i==0)upcoming else history,text={Text(LocaleStrings.get(text))},unselectedContentColor=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag(if(i==0)"my-upcoming" else "my-results"))}
    }
}

@Composable
internal fun ResultCard(row:ResultSummary,open:()->Unit) {
    Card(onClick=open,modifier=Modifier.fillMaxWidth().testTag("result-${row.id}"),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Image(painterResource(modeArtwork(row.mode)),null,Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(row.title,style=MaterialTheme.typography.titleMedium)
                    Text(listOf(LocaleStrings.get(row.mode).replaceFirstChar{it.titlecase()},row.map,row.time).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.ic_chevron),null,Modifier.size(18.dp))
            }
            val stats=listOf("Placement" to if(row.placement>0)"#${row.placement}" else "—","Kills" to row.kills.toString(),"Award" to compactMoney(row.award))
            if(LocalDensity.current.fontScale>1.3f)Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                stats.forEach{(label,value)->LabeledValue(LocaleStrings.get(label),value)}
            }else Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                stats.forEach{(label,value)->Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(LocaleStrings.get(label),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value,style=MaterialTheme.typography.titleMedium)
                }}
            }
            Text(if(row.settlement=="credited")compactMoney(row.credited)+" · "+LocaleStrings.get("Credited to winnings") else LocaleStrings.get(if(row.settlement=="pending")"Not credited · awaiting settlement" else "No prize credit applies"),style=MaterialTheme.typography.bodySmall,color=if(row.settlement=="credited")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ResultBreakdown(row:ResultSummary) {
    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(LocaleStrings.get("Reward breakdown"),style=MaterialTheme.typography.titleLarge)
        Text(row.title,style=MaterialTheme.typography.titleMedium)
        Text(row.username,style=MaterialTheme.typography.bodyMedium)
        Text(LocaleStrings.get("Match ID")+" · "+row.match,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(row.time,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        LabeledValue(LocaleStrings.get("Placement"),if(row.placement>0)"#${row.placement}" else LocaleStrings.get("Placement not recorded"))
        LabeledValue(LocaleStrings.get("Kills"),row.kills.toString())
        if(row.version==2L) {
            LabeledValue(LocaleStrings.get("Placement reward"),compactMoney(row.placementReward))
            LabeledValue(LocaleStrings.get("Kill reward credited"),compactMoney(row.killReward))
            if(!row.killsFinal)Text(LocaleStrings.get("Kill rewards pending"),style=MaterialTheme.typography.bodySmall)
        }
        LabeledValue(LocaleStrings.get("Announced award"),compactMoney(row.award))
        if(row.settlement=="credited")LabeledValue(LocaleStrings.get("Credited to winnings"),compactMoney(row.credited))
        else Text(LocaleStrings.get(if(row.settlement=="pending")"Not credited · awaiting settlement" else "No prize credit applies"),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun PendingResultCard(match:Tournament,time:String) {
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
        Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Image(painterResource(modeArtwork(match.mode)),null,Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(match.title,style=MaterialTheme.typography.titleMedium)
                Text(listOf(LocaleStrings.get(match.mode),match.map,time).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(LocaleStrings.get(if(match.matchStatus=="cancelled")"Cancelled" else "Results pending"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
