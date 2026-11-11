package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun noticeDateGroup(notice:Notice,now:Long,bangla:Boolean):String {
    if(notice.updatedAt<=0)return LocaleStrings.get("Announcements")
    val date=Instant.ofEpochMilli(notice.updatedAt).atZone(ZoneId.of("Asia/Dhaka")).toLocalDate()
    val today=Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Dhaka")).toLocalDate()
    return when(date){today->LocaleStrings.get("Today");today.minusDays(1)->LocaleStrings.get("Yesterday");else->date.format(DateTimeFormatter.ofPattern("dd MMM yyyy",java.util.Locale(if(bangla)"bn" else "en")))}
}

internal fun noticeDateTime(notice:Notice):String=if(notice.updatedAt<=0)LocaleStrings.get("Awaiting server timestamp") else Instant.ofEpochMilli(notice.updatedAt).atZone(ZoneId.of("Asia/Dhaka")).format(DateTimeFormatter.ofPattern("dd MMM · h:mm a",java.util.Locale.ENGLISH))

@Composable
internal fun NotificationItem(notice:Notice,unread:Boolean,open:()->Unit) {
    Card(onClick=open,modifier=Modifier.fillMaxWidth().testTag("notice-${notice.id}").semantics{stateDescription=LocaleStrings.get(if(unread)"Unread" else "All read")},shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
        Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_bell),null,Modifier.size(22.dp),tint=if(unread)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(notice.title,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(unread)Badge(containerColor=MaterialTheme.colorScheme.primary,modifier=Modifier.size(6.dp))
                }
                Text(notice.body,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(noticeDateTime(notice),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun AnnouncementReader(notice:Notice) {
    SelectionContainer {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(LocaleStrings.get("Announcement"),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(noticeDateTime(notice),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(notice.title,style=MaterialTheme.typography.headlineSmall)
            Text(notice.body,style=MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
internal fun NotificationFeedStatus(feed:NoticeFeed,retry:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(feed.loading&&feed.notices.isEmpty())repeat(2){LoadingPlaceholder(Modifier.fillMaxWidth().height(104.dp))}
        if(feed.error||feed.cached) {
            Text(LocaleStrings.get(if(feed.error)"Could not refresh announcements." else "Offline · Showing saved announcements"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick=retry){Text(LocaleStrings.get(if(feed.error)"Retry" else "Refresh"))}
        }
        if(!feed.loading&&!feed.error&&feed.notices.isEmpty())Text(LocaleStrings.get("No notifications yet."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
