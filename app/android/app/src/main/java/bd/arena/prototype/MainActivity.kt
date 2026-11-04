package bd.arena.prototype

import androidx.compose.animation.togetherWith
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFFC4DF9B)
private val Background = Color(0xFF101311)
private val Surface = Color(0xFF1B211D)
private val Muted = Color(0xFFABB5AD)
private val Foreground = Color(0xFFF1F3ED)
private val Border = Color(0xFF6B786D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(shapes=Shapes(small=RoundedCornerShape(8.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(20.dp),extraLarge=RoundedCornerShape(24.dp)),colorScheme = darkColorScheme(primary = Accent, onPrimary = Background,
                background = Background, surface = Surface, onSurface = Foreground,
                onBackground = Foreground, onSurfaceVariant = Muted, outline = Border,
                secondaryContainer = Surface, onSecondaryContainer = Accent, surfaceVariant = Color(0xFF273029), outlineVariant=Color(0xFF364239))) { Surface(Modifier.fillMaxSize(),color=Background) { AntihackTourApp() } }
        }
    }
}

@Composable
private fun AntihackTourApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings",0) }
    var bangla by remember { mutableStateOf(prefs.getBoolean("bangla",false)) }
    LocaleStrings.configure(context,bangla)
    val session = remember { AdminSession(context) }
    DisposableEffect(session) { onDispose { session.close() } }
    val font = if(bangla) androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.hind_siliguri)) else androidx.compose.ui.text.font.FontFamily.Default
    val base=MaterialTheme.typography
    fun localized(style:androidx.compose.ui.text.TextStyle)=style.copy(fontFamily=font,fontSize=if(bangla)style.fontSize*0.90f else style.fontSize,lineHeight=if(bangla)style.lineHeight*0.92f else style.lineHeight)
    MaterialTheme(typography=Typography(displayLarge=localized(base.displayLarge),displayMedium=localized(base.displayMedium),displaySmall=localized(base.displaySmall),headlineLarge=localized(base.headlineLarge),headlineMedium=localized(base.headlineMedium),headlineSmall=localized(base.headlineSmall),titleLarge=localized(base.titleLarge),titleMedium=localized(base.titleMedium),titleSmall=localized(base.titleSmall),bodyLarge=localized(base.bodyLarge),bodyMedium=localized(base.bodyMedium),bodySmall=localized(base.bodySmall),labelLarge=localized(base.labelLarge),labelMedium=localized(base.labelMedium),labelSmall=localized(base.labelSmall))) {
        val changeLanguage: (Boolean)->Unit = { bangla=it;prefs.edit().putBoolean("bangla",it).apply() }
        PlayerApp(session,bangla,changeLanguage)
    }
}

@Composable
private fun PageTitle(text: String) { Text(text, modifier = Modifier.fillMaxWidth(), fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp) }
@Composable
private fun SectionTitle(text: String) { Text(LocaleStrings.get(text), fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
@Composable
private fun Secondary(text: String, small: Boolean = false) { Text(LocaleStrings.get(text), color = Muted, fontSize = if (small) 13.sp else 16.sp, lineHeight = if (small) 20.sp else 24.sp) }
@Composable
private fun AppIcon(resource: Int, description: String? = null, tint: Color = LocalContentColor.current) { Icon(painterResource(resource), contentDescription = description, modifier = Modifier.size(24.dp), tint = tint) }
private fun modeResource(mode: String) = when (mode) { "Duo" -> R.drawable.mode_duo; "Squad" -> R.drawable.mode_squad; else -> R.drawable.mode_solo }
@Composable
private fun ModeImage(mode: String, modifier: Modifier) { Image(painterResource(modeResource(mode)), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier) }
@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content) }
@Composable
private fun DetailLine(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) { Text(label, color = Muted, modifier = Modifier.weight(1f)); Text(value, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium) } }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModeChoices(open: (String) -> Unit) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Solo", "Duo", "Squad").forEach { mode ->
                Card(onClick = { open(mode) }, modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Surface, contentColor = Foreground)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ModeImage(mode, Modifier.size(80.dp))
                        Text(LocaleStrings.get(mode.lowercase()), modifier = Modifier.weight(1f).padding(vertical = 16.dp), fontSize = 16.sp)
                        AppIcon(R.drawable.ic_chevron, tint = Muted)
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
    } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("Solo", "Duo", "Squad").forEach { mode ->
            Card(onClick = { open(mode) }, modifier = Modifier.weight(1f).semantics { role = Role.Button }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Surface, contentColor = Foreground)) {
                ModeImage(mode, Modifier.fillMaxWidth().aspectRatio(1f))
                Text(LocaleStrings.get(mode.lowercase()), modifier = Modifier.padding(12.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun status(match: Tournament, registered: Boolean): String = when {
    registered -> "Joined"
    match.closed -> "Closed"
    match.joined >= match.capacity -> "Full"
    else -> "${match.capacity - match.joined} spots left"
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchRow(match: Tournament, registered: Boolean, open: (String) -> Unit) {
    Card(onClick = { open(match.id) }, modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Surface, contentColor = Foreground)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ModeImage(match.mode, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${match.mode} · ${money(match.fee)}", color = Accent, fontSize = 12.sp)
                Text(match.title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Secondary(match.time, small = true)
                Secondary(status(match, registered), small = true)
            }
            AppIcon(R.drawable.ic_chevron, tint = Muted)
        }
    }
}
@Composable
private fun EmptyState(icon: Int, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppIcon(icon, tint = Accent)
        SectionTitle(title)
        Secondary(subtitle)
    }
}


@Composable
internal fun HomeHero(bangla: Boolean) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)) {
        Image(painterResource(R.drawable.home_hero),null,Modifier.fillMaxWidth().aspectRatio(2.25f),contentScale=ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Background.copy(alpha=0.1f),Background.copy(alpha=0.95f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("FREE FIRE / BANGLADESH",style=MaterialTheme.typography.labelSmall,color=Accent)
            Text(if(bangla)"আপনার পরের জয়।" else "Your next victory awaits.",style=MaterialTheme.typography.titleLarge,color=Foreground)
        }
    }
}

private fun noticeTime(notice: Notice) = if (notice.updatedAt == 0L) "Sample announcement" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(notice.updatedAt))

@Composable
private fun NoticeConnection(feed: NoticeFeed, retry: () -> Unit) {
    when {
        feed.loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent)
        feed.error -> Column { Secondary("Announcements couldn’t be refreshed. Saved updates may be out of date.", small = true); TextButton(onClick = retry) { Text("Try again") } }
        feed.cached -> Column { Secondary("Offline · Showing saved announcements", small = true); TextButton(onClick = retry) { Text("Refresh") } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnnouncementPanel(feed: NoticeFeed, unread: Int, openAll: () -> Unit, openNotice: (Notice) -> Unit, retry: () -> Unit) {
    if (feed.loading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent)
        return
    }
    if (feed.error) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Secondary(LocaleStrings.get("Could not refresh announcements."), small = true)
            TextButton(onClick = retry) { Text(LocaleStrings.get("Retry")) }
        }
        return
    }
    val notice = feed.notices.firstOrNull() ?: return
    Card(onClick = { openNotice(notice) }, modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Surface, contentColor = Foreground)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = Accent.copy(alpha = 0.15f)) {
                Box(Modifier.padding(8.dp)) {
                    AppIcon(R.drawable.ic_bell, tint = Accent)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(notice.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (unread > 0) Surface(onClick = openAll, shape = RoundedCornerShape(8.dp), color = Accent) {
                        Text(if (unread == 1) "New" else "$unread New", color = Background, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Text(notice.body, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 12.sp)
            }
            AppIcon(R.drawable.ic_chevron, tint = Muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoticeCard(notice: Notice, unread: Boolean, open: () -> Unit) {
    Card(onClick = open, modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Surface, contentColor = Foreground)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (unread) Text("New", color = Accent, fontSize = 12.sp)
            Text(notice.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(notice.body, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 16.sp, lineHeight = 24.sp)
            Secondary(noticeTime(notice), small = true)
        }
    }
}
