package bd.arena.prototype

import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.graphicsLayer

@Composable
internal fun HomeEntrance(content:@Composable ()->Unit) {
    var visible by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){visible=true}
    val progress by animateFloatAsState(if(visible)1f else 0f,tween(220),label="Home content entrance")
    Box(Modifier.graphicsLayer{alpha=progress;translationY=(1f-progress)*8.dp.toPx()}){content()}
}

internal fun modeArtwork(mode:String)=when(mode.lowercase()){ "duo"->R.drawable.mode_duo;"squad"->R.drawable.mode_squad;else->R.drawable.mode_solo }
@Composable
internal fun ScreenHeader(title:String,back:(()->Unit)?=null,refresh:(()->Unit)?=null,refreshEnabled:Boolean=true,actions:@Composable RowScope.()->Unit={}) {
    if(refresh!=null&&LocalFixedRefresh.current!=null)RegisterRefresh(refreshEnabled,refresh)
    Row(Modifier.fillMaxWidth().testTag("screen-header"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(back!=null)IconButton(onClick=back){Icon(painterResource(R.drawable.ic_back),LocaleStrings.get("Back"))}
        if(title=="Antihack Tour")Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            BrandLogo(Modifier.size(76.dp).testTag("home-logo"))
            if(LocalDensity.current.fontScale<=1.3f)Column(Modifier.weight(1f).clearAndSetSemantics{},verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(buildAnnotatedString{append("ANTI");withStyle(SpanStyle(color=MaterialTheme.colorScheme.primary)){append("HACK")}},
                    style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.Bold,letterSpacing=1.sp),maxLines=1)
                Text("TOUR",style=MaterialTheme.typography.labelSmall.copy(letterSpacing=2.sp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        else Text(title,style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f).semantics{heading()})
        actions()
        if(refresh!=null&&LocalFixedRefresh.current==null)IconButton(onClick=refresh,enabled=refreshEnabled){Icon(painterResource(R.drawable.ic_refresh),LocaleStrings.get("Refresh"))}
    }
}
@Composable
internal fun NotificationAction(unread:Int,open:()->Unit) {
    // Badge is a sibling of the circular button, with its full bounds inside this action.
    Box(Modifier.sizeIn(minWidth=56.dp,minHeight=56.dp).testTag("notification-action")) {
        IconButton(onClick=open,modifier=Modifier.matchParentSize().semantics{stateDescription=if(unread>0)LocaleStrings.get("Unread")+": $unread" else LocaleStrings.get("All read")}) {
            Icon(painterResource(R.drawable.ic_bell),LocaleStrings.get("Notifications"))
        }
        if(unread>0)Badge(modifier=Modifier.align(Alignment.TopEnd).padding(2.dp).clearAndSetSemantics{testTag="notification-badge"},containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary) {
            Text(if(unread>99)"99+" else unread.toString(),maxLines=1)
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

@Composable
internal fun BrandLogo(modifier:Modifier=Modifier) {
    Image(painterResource(R.drawable.brand_logo),"Antihack Tour",modifier,contentScale=ContentScale.Fit)
}
