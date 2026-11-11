package bd.arena.prototype

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun LoadingPlaceholder(modifier:Modifier) {
    val surface=MaterialTheme.colorScheme.surfaceVariant
    val highlight=MaterialTheme.colorScheme.onSurface.copy(alpha=0.05f)
    val motion=ValueAnimator.areAnimatorsEnabled()
    val position=if(motion) {
        val transition=rememberInfiniteTransition(label="Content loading")
        transition.animateFloat(-1f,2f,infiniteRepeatable(tween(1600,easing=LinearEasing)),label="Loading highlight").value
    }else 0f
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(surface).drawWithContent {
        drawContent()
        if(motion) {
            val start=size.width*position
            drawRect(Brush.linearGradient(listOf(surface,highlight,surface),Offset(start,0f),Offset(start+size.width,size.height)))
        }
    }.clearAndSetSemantics{})
}

@Composable
internal fun HomeCategoryLoading() {
    Row(Modifier.fillMaxWidth().testTag("home-categories-loading").semantics{contentDescription=LocaleStrings.get("Loading categories…")},horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        repeat(2){LoadingPlaceholder(Modifier.weight(1f).aspectRatio(0.78f))}
    }
}

@Composable
internal fun HomeStartup() {
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
        Column(Modifier.widthIn(max=640.dp).fillMaxWidth().safeDrawingPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            ScreenHeader("Antihack Tour")
            LoadingPlaceholder(Modifier.fillMaxWidth().height(112.dp))
            HomeCategoryLoading()
        }
    }
}

@Composable
internal fun HomeCategoryHeading() {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(LocaleStrings.get("Your next challenge"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(LocaleStrings.get("Choose your arena."),style=MaterialTheme.typography.headlineMedium,modifier=Modifier.semantics{heading()})
    }
}

@Composable
internal fun HomeAnnouncement(feed:NoticeFeed,open:(Notice)->Unit,retry:()->Unit) {
    val notice=feed.notices.firstOrNull()
    if(feed.loading&&notice==null) {
        Box(Modifier.testTag("home-announcement-loading").semantics{contentDescription=LocaleStrings.get("Loading announcements…")}) {
            LoadingPlaceholder(Modifier.fillMaxWidth().height(112.dp))
        }
        return
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(notice!=null)HomeEntrance {
            Card(onClick={open(notice)},shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().testTag("home-announcement"),
                colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface),
                border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(LocaleStrings.get("Latest update"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(notice.title,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall.copy(fontWeight=FontWeight.SemiBold),maxLines=2,overflow=TextOverflow.Ellipsis)
                        Icon(painterResource(R.drawable.ic_chevron),null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(notice.body,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
        if(feed.error||feed.cached) {
            Column {
                Text(LocaleStrings.get(if(feed.error)"Could not refresh announcements." else "Offline · Showing saved announcements"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick=retry){Text(LocaleStrings.get(if(feed.error)"Retry" else "Refresh"))}
            }
        }
    }
}

@Composable
internal fun HomeExploreShortcut(open:()->Unit) {
    Surface(onClick=open,color=MaterialTheme.colorScheme.background,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().testTag("home-explore-shortcut")) {
        Row(Modifier.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_explore),null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(LocaleStrings.get("Looking for a match?"),style=MaterialTheme.typography.titleSmall)
                Text(LocaleStrings.get("Browse every category in Explore."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface),contentAlignment=Alignment.Center) {
                Icon(painterResource(R.drawable.ic_chevron),null,Modifier.size(20.dp))
            }
        }
    }
}
