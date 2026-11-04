package bd.arena.prototype

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

internal fun modeArtwork(mode:String)=when(mode.lowercase()){ "duo"->R.drawable.mode_duo;"squad"->R.drawable.mode_squad;else->R.drawable.mode_solo }
@Composable
internal fun ScreenHeader(title:String,back:(()->Unit)?=null,refresh:(()->Unit)?=null,refreshEnabled:Boolean=true,actions:@Composable RowScope.()->Unit={}) {
    Row(Modifier.fillMaxWidth().testTag("screen-header"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(back!=null)IconButton(onClick=back){Icon(painterResource(R.drawable.ic_back),LocaleStrings.get("Back"))}
        if(title=="Antihack Tour")Box(Modifier.weight(1f)){BrandLogo(Modifier.size(76.dp).testTag("home-logo"))}
        else Text(title,style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f).semantics{heading()})
        actions()
        if(refresh!=null)IconButton(onClick=refresh,enabled=refreshEnabled){Icon(painterResource(R.drawable.ic_refresh),LocaleStrings.get("Refresh"))}
    }
}
@Composable
internal fun NotificationAction(unread:Int,open:()->Unit) {
    // Badge is a sibling of the circular button, with its full bounds inside this action.
    Box(Modifier.sizeIn(minWidth=56.dp,minHeight=56.dp).testTag("notification-action")) {
        IconButton(onClick=open,modifier=Modifier.matchParentSize().semantics{stateDescription=if(unread>0)LocaleStrings.get("Unread")+": $unread" else LocaleStrings.get("All read")}) {
            Icon(painterResource(R.drawable.ic_bell),LocaleStrings.get("Notifications"))
        }
        if(unread>0)Badge(Modifier.align(Alignment.TopEnd).padding(2.dp).clearAndSetSemantics{testTag="notification-badge"}) {
            Text(if(unread>99)"99+" else unread.toString(),maxLines=1)
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatchSummaryCard(session:AdminSession,match:Tournament,entry:String?,open:()->Unit) {
    Card(onClick=open,modifier=Modifier.fillMaxWidth().testTag("match-card-${match.id}"),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
        ArtworkImage(session,match.artworkId,modeArtwork(match.image),Modifier.fillMaxWidth().height(190.dp).testTag("match-image-${match.id}"))
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(match.title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    Icon(painterResource(R.drawable.ic_chevron),null,Modifier.size(20.dp))
                }
                Text(listOf(LocaleStrings.get(match.mode.lowercase()),match.map).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(match.time,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MatchCountdown(match)
            Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(LocaleStrings.get("Entry fee"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(money(match.fee),style=MaterialTheme.typography.titleLarge)}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(LocaleStrings.get("Prize pool"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(money(match.prize),style=MaterialTheme.typography.titleLarge)}
            }
            val statusText=when {
                match.matchStatus=="cancelled" -> LocaleStrings.get("Cancelled")
                entry=="confirmed" -> LocaleStrings.get("View room details")
                entry!=null -> LocaleStrings.get(entry)
                match.closed -> LocaleStrings.get("Closed")
                match.joined>=match.capacity -> LocaleStrings.get("Match full")
                else -> null
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${match.joined} / ${match.capacity} "+LocaleStrings.get("Participants"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                statusText?.let {Text(it,style=MaterialTheme.typography.labelLarge,color=if(entry=="confirmed")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }
    }
}
@Composable
internal fun MatchOverview(session:AdminSession,match:Tournament) {
    Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
        ArtworkImage(session,match.artworkId,modeArtwork(match.image),Modifier.fillMaxWidth().height(190.dp))
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(LocaleStrings.get(match.mode.lowercase())+if(match.map.isNotBlank())" · ${match.map}" else "",style=MaterialTheme.typography.titleMedium)
                Text(match.time,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MatchCountdown(match)
            Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                LabeledValue(LocaleStrings.get("Entry fee"),money(match.fee))
                LabeledValue(LocaleStrings.get("Prize pool"),money(match.prize))
                LabeledValue(LocaleStrings.get("Participants"),"${match.joined} / ${match.capacity}")
            }
            Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                LabeledValue(LocaleStrings.get("Match ID"),match.id)
                LabeledValue(LocaleStrings.get("Status"),LocaleStrings.get(match.matchStatus))
            }
            if(match.description.isNotBlank())Text(match.description,style=MaterialTheme.typography.bodyLarge)
            if(match.matchStatus=="cancelled")Text(match.cancellationReason.ifBlank{LocaleStrings.get("Contact support for cancellation details.")},style=MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable
internal fun LabeledValue(label:String,value:String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(label,Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun MatchFilter(label:String,options:List<String>,selected:String,enabled:Boolean=true,choose:(String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth()) { Text(label+": "+LocaleStrings.get(selected)) }
        DropdownMenu(expanded,onDismissRequest={expanded=false}) {
            options.forEach { option -> DropdownMenuItem(text={Text(LocaleStrings.get(option))},onClick={choose(option);expanded=false}) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatchCountdown(match:Tournament) {
    var now by remember(match.startsAtMillis){mutableStateOf(System.currentTimeMillis())}
    LaunchedEffect(match.startsAtMillis){while(match.startsAtMillis>now){kotlinx.coroutines.delay(1000);now=System.currentTimeMillis()}}
    val scheduled=match.matchStatus in listOf("open","closed")&&match.startsAtMillis>now
    val highlight=scheduled||match.matchStatus=="live"
    val textColor=if(highlight)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(color=if(highlight)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) {
        FlowRow(Modifier.padding(horizontal=12.dp,vertical=10.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(LocaleStrings.get(if(scheduled)"Starts in" else when(match.matchStatus){"live"->"Live now";"review"->"Results pending";"settled"->"Completed";"cancelled"->"Cancelled";else->"Scheduled start reached"}),style=MaterialTheme.typography.labelLarge,color=textColor,modifier=Modifier.padding(end=16.dp))
            if(scheduled) {
                val seconds=((match.startsAtMillis-now).coerceAtLeast(0)+999)/1000
                val clock=listOf(seconds%86400/3600,seconds%3600/60,seconds%60).joinToString(":"){it.toString().padStart(2,'0')}
                Text((if(seconds>=86400)"${seconds/86400}d · " else "")+clock,style=MaterialTheme.typography.titleMedium.copy(fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace,fontFeatureSettings="tnum"),color=textColor)
            }
        }
    }
}

@Composable
internal fun BrandLogo(modifier:Modifier=Modifier) {
    Image(painterResource(R.drawable.brand_logo),"Antihack Tour",modifier,contentScale=ContentScale.Fit)
}
