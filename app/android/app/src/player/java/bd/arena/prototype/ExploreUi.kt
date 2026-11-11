package bd.arena.prototype

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExploreControls(search:String,changeSearch:(String)->Unit,mode:String,changeMode:(String)->Unit,filterCount:Int,openFilters:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            CompactSearch(search,changeSearch,LocaleStrings.get("Search match ID or title"),Modifier.weight(1f).testTag("explore-search"))
            FilledTonalIconButton(onClick=openFilters,modifier=Modifier.size(48.dp).testTag("explore-filters").semantics {
                contentDescription=LocaleStrings.get("Filters")+if(filterCount>0)": $filterCount" else ""
            }) {
                BadgedBox(badge={if(filterCount>0)Badge(Modifier.clearAndSetSemantics{},containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary){Text(filterCount.toString())}}) {
                    Icon(painterResource(R.drawable.ic_filter),null)
                }
            }
        }
        // FilterChip draws a compact 32dp pill while retaining Material's 48dp touch target.
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
            listOf("All","Solo","Duo","Squad").forEach { option ->
                FilterChip(selected=mode==option,onClick={changeMode(option)},
                    label={Text(exploreModeLabel(option),style=MaterialTheme.typography.labelLarge)},
                    shape=RoundedCornerShape(50),modifier=Modifier.testTag("explore-mode-$option"))
            }
        }
    }
}

private fun exploreModeLabel(mode:String)=LocaleStrings.get(if(mode=="All")mode else mode.lowercase(java.util.Locale.ROOT)).replaceFirstChar{it.titlecase(java.util.Locale.ROOT)}

internal fun exploreStartText(match:Tournament,now:Long):String {
    if(match.matchStatus in listOf("open","closed")&&match.startsAtMillis>now) {
        val seconds=(match.startsAtMillis-now+999)/1000
        val clock=listOf(seconds%86400/3600,seconds%3600/60,seconds%60).joinToString(":"){it.toString().padStart(2,'0')}
        return LocaleStrings.get("Starts in")+" "+(if(seconds>=86400)"${seconds/86400}"+LocaleStrings.get("d")+" · " else "")+clock
    }
    return LocaleStrings.get(when(match.matchStatus) {
        "live"->"Live now";"review"->"Results pending";"settled"->"Completed";"cancelled"->"Cancelled"
        else->if(match.startsAtMillis>0)"Scheduled start reached" else "Scheduled"
    })
}

@Composable
internal fun ExploreMatchCard(session:AdminSession,match:Tournament,entry:String?,now:Long,open:()->Unit) {
    ExploreMatchCard(match,entry,now,open) { modifier ->
        ArtworkImage(session,match.artworkId,modeArtwork(match.image),modifier)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExploreMatchCard(match:Tournament,entry:String?,now:Long,open:()->Unit,artwork:@Composable (Modifier)->Unit) {
    Card(onClick=open,modifier=Modifier.fillMaxWidth().testTag("match-card-${match.id}"),shape=RoundedCornerShape(20.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
        Box(Modifier.fillMaxWidth().heightIn(min=108.dp).testTag("match-image-${match.id}")) {
            artwork(Modifier.matchParentSize())
            Surface(color=MaterialTheme.colorScheme.background.copy(alpha=0.96f),contentColor=MaterialTheme.colorScheme.onSurface,
                shape=RoundedCornerShape(10.dp),modifier=Modifier.align(Alignment.TopEnd).padding(10.dp).testTag("explore-start-${match.id}")) {
                Row(Modifier.padding(horizontal=9.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    Icon(painterResource(R.drawable.ic_clock),null,Modifier.size(14.dp),tint=MaterialTheme.colorScheme.primary)
                    Text(exploreStartText(match,now),style=MaterialTheme.typography.labelSmall.copy(fontFeatureSettings="tnum"))
                }
            }
        }
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(match.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.SemiBold))
                    Icon(painterResource(R.drawable.ic_chevron),null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(listOf(exploreModeLabel(match.mode),match.map,match.time).filter{it.isNotBlank()}.joinToString(" · "),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    listOf("Entry fee","Prize pool").forEach { label ->
                        Text(LocaleStrings.get(label),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    listOf(match.fee,match.prize).forEach { value ->
                        Text(money(value).removeSuffix(".00"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.SemiBold))
                    }
                }
            }
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                val filled=if(match.capacity>0)(match.joined.toFloat()/match.capacity).coerceIn(0f,1f) else 0f
                val progress by animateFloatAsState(filled,tween(220),label="Match participation")
                // The exact count below is accessible; the thin bar is only a visual summary.
                LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(3.dp).clearAndSetSemantics{},trackColor=MaterialTheme.colorScheme.surfaceVariant)
                val status=when {
                    match.matchStatus=="cancelled"->"Cancelled"
                    entry=="confirmed"->"View room details"
                    entry!=null->entry
                    match.closed->"Closed"
                    match.joined>=match.capacity->"Match full"
                    else->null
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("${match.joined} / ${match.capacity} "+LocaleStrings.get("joined"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    status?.let{Text(LocaleStrings.get(it),style=MaterialTheme.typography.labelMedium,color=if(entry=="confirmed"&&match.matchStatus!="cancelled")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                }
            }
        }
    }
}

@Composable
internal fun ExploreLoadingCard() {
    Column(Modifier.fillMaxWidth().testTag("explore-loading").semantics{contentDescription=LocaleStrings.get("Loading matches…")},verticalArrangement=Arrangement.spacedBy(12.dp)) {
        LoadingPlaceholder(Modifier.fillMaxWidth().height(108.dp))
        LoadingPlaceholder(Modifier.fillMaxWidth(0.7f).height(24.dp))
        LoadingPlaceholder(Modifier.fillMaxWidth().height(72.dp))
    }
}
