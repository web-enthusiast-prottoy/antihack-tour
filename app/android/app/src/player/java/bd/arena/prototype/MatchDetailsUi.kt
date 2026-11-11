package bd.arena.prototype

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun MatchDetailsHero(session:AdminSession,match:Tournament,now:Long) {
    MatchDetailsHero(match,now){modifier->ArtworkImage(session,match.artworkId,modeArtwork(match.image),modifier)}
}

@Composable
internal fun MatchDetailsHero(match:Tournament,now:Long,artwork:@Composable (Modifier)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(shape=RoundedCornerShape(20.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min=156.dp).testTag("details-artwork")) {
                artwork(Modifier.matchParentSize())
                Surface(color=MaterialTheme.colorScheme.background.copy(alpha=0.96f),contentColor=MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(10.dp),
                    modifier=Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                    Row(Modifier.padding(horizontal=9.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        Icon(painterResource(R.drawable.ic_clock),null,Modifier.size(14.dp),tint=MaterialTheme.colorScheme.primary)
                        Text(exploreStartText(match,now),style=MaterialTheme.typography.labelSmall.copy(fontFeatureSettings="tnum"))
                    }
                }
            }
        }
        Text(match.title,style=MaterialTheme.typography.headlineSmall.copy(fontWeight=FontWeight.SemiBold),modifier=Modifier.semantics{heading()})
        Text(listOf(LocaleStrings.get(match.mode.lowercase()).replaceFirstChar{it.titlecase()},match.map,match.time).filter{it.isNotBlank()}.joinToString(" · "),
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(16.dp)) {
            val values=listOf("Entry fee" to money(match.fee).removeSuffix(".00"),"Prize pool" to money(match.prize).removeSuffix(".00"),"Participants" to "${match.joined} / ${match.capacity}")
            if(LocalDensity.current.fontScale>1.3f)Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                values.forEach{(label,value)->LabeledValue(LocaleStrings.get(label),value)}
            } else Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                values.forEach{(label,value)->Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(LocaleStrings.get(label),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value,style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.SemiBold))
                }}
            }
        }
    }
}

@Composable
internal fun MatchDetailsTabs(selected:String,choose:(String)->Unit) {
    val tabs=listOf("Overview","Prizes","Players")
    if(LocalDensity.current.fontScale>1.3f)Column(Modifier.fillMaxWidth()) {
        tabs.forEach{tab->FilterChip(selected==tab,{choose(tab)},label={Text(LocaleStrings.get(tab))},modifier=Modifier.testTag("details-tab-$tab"))}
    } else TabRow(tabs.indexOf(selected).coerceAtLeast(0),containerColor=MaterialTheme.colorScheme.background) {
        tabs.forEach{tab->Tab(selected==tab,onClick={choose(tab)},text={Text(LocaleStrings.get(tab))},selectedContentColor=MaterialTheme.colorScheme.primary,unselectedContentColor=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("details-tab-$tab"))}
    }
}

@Composable
internal fun MatchRules(match:Tournament)=MatchTextSection(match.id,"match-rules","Match rules",match.rules)

@Composable
internal fun MatchDescription(match:Tournament) {
    if(match.description.isNotBlank())MatchTextSection(match.id,"match-description","Description",match.description)
}

@Composable
private fun MatchTextSection(matchId:String,tag:String,title:String,body:String) {
    var expanded by rememberSaveable(matchId,tag){mutableStateOf(true)}
    Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("$tag-toggle").semantics {
            stateDescription=LocaleStrings.get(if(expanded)"Expanded" else "Collapsed")
        },contentPadding=PaddingValues(16.dp)) {
            Text(LocaleStrings.get(title),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onSurface)
            Text(if(expanded)"−" else "+",style=MaterialTheme.typography.titleMedium)
        }
        AnimatedVisibility(expanded,enter=expandVertically(tween(180))+fadeIn(tween(180)),exit=shrinkVertically(tween(160))+fadeOut(tween(160))) {
            Text(body,Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,bottom=16.dp).testTag("$tag-body"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun MatchOverviewPanel(match:Tournament,room:@Composable ()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {
        MatchDescription(match)
        MatchRules(match)
        room()
        MatchDetailsMetadata(match)
        if(match.matchStatus=="cancelled")Text(match.cancellationReason.ifBlank{LocaleStrings.get("Contact support for cancellation details.")},style=MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatchDetailsMetadata(match:Tournament) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.testTag("details-metadata")) {
        Text(LocaleStrings.get("Match ID")+" · "+match.id,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(LocaleStrings.get("Status")+" · "+LocaleStrings.get(match.matchStatus),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun MatchJoinBar(fee:Long,label:String,enabled:Boolean,busy:Boolean,join:()->Unit) {
    Surface(color=MaterialTheme.colorScheme.background) {
        val large=LocalDensity.current.fontScale>1.3f
        @Composable fun Fee() {Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(LocaleStrings.get("Entry fee"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(fee).removeSuffix(".00"),style=MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.SemiBold))
        }}
        @Composable fun Action(modifier:Modifier) {Button(onClick=join,enabled=enabled,modifier=modifier.heightIn(min=52.dp).testTag("match-join")) {
            if(busy)CircularProgressIndicator(Modifier.padding(end=8.dp).size(18.dp),strokeWidth=2.dp)
            Text(label)
        }}
        if(large)Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {Fee();Action(Modifier.fillMaxWidth())}
        else Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.CenterVertically) {Fee();Action(Modifier.weight(1f))}
    }
}

@Composable
internal fun JoinRequirements(match:Tournament,missingName:Boolean,balance:Long,continueJoin:()->Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(LocaleStrings.get("Before you join"),style=MaterialTheme.typography.titleLarge)
        Text(match.title,style=MaterialTheme.typography.titleMedium)
        if(missingName)Text(LocaleStrings.get("Add your Free Fire username to join this match."),style=MaterialTheme.typography.bodyMedium)
        if(balance<match.fee) {
            LabeledValue(LocaleStrings.get("Entry fee"),money(match.fee).removeSuffix(".00"))
            LabeledValue(LocaleStrings.get("Available to join"),money(balance).removeSuffix(".00"))
            LabeledValue(LocaleStrings.get("Add money"),money(match.fee-balance).removeSuffix(".00"))
        }
        Button(onClick=continueJoin,modifier=Modifier.fillMaxWidth().testTag("join-requirement-action")){Text(LocaleStrings.get(if(missingName)"Add username" else "Add money"))}
    }
}
