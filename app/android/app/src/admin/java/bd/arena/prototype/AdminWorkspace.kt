package bd.arena.prototype

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

internal enum class AdminDestination(val label: String, val icon: Int) {
    Overview("Overview", R.drawable.ic_home),
    Matches("Matches", R.drawable.ic_matches),
    Finance("Finance", R.drawable.ic_wallet),
    Players("Players", R.drawable.ic_profile),
    Settings("Settings", R.drawable.ic_settings)
}

@Composable
internal fun AdminWorkspace(
    destination: AdminDestination,
    navigate: (AdminDestination) -> Unit,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize()) {
                Surface(Modifier.width(224.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(painterResource(R.drawable.admin_brand_logo), null, Modifier.size(32.dp))
                            Text("Antihack Admin", style = MaterialTheme.typography.titleMedium)
                        }
                        AdminDestination.values().forEach { item ->
                            NavigationDrawerItem(
                                modifier = Modifier.testTag("admin-nav-${item.name}"),
                                label = { Text(adminText(item.label)) },
                                icon = { Icon(painterResource(item.icon), null) },
                                selected = destination == item,
                                onClick = { navigate(item) }
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { FixedRefreshHost { content() } }
            }
        } else {
            val largeText = LocalDensity.current.fontScale > 1.35f
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(windowInsets = WindowInsets(0)) {
                        AdminDestination.values().forEach { item ->
                            val label = adminText(item.label)
                            NavigationBarItem(
                                modifier = Modifier.testTag("admin-nav-${item.name}"),
                                selected = destination == item,
                                onClick = { navigate(item) },
                                icon = { Icon(painterResource(item.icon), if (largeText) label else null) },
                                label = if (largeText) null else { { Text(label, maxLines = 1) } }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) { FixedRefreshHost { content() } }
            }
        }
    }
}

@Composable
internal fun AdminLanding(
    title: String,
    showLogo: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showLogo) {
                Image(painterResource(R.drawable.admin_brand_logo), null, Modifier.size(40.dp))
            }
            Text(adminText(title), style = MaterialTheme.typography.headlineMedium)
        }
        content()
    }
}

@Composable
internal fun AdminRow(title: String, detail: String? = null, status: String? = null, open: () -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.35f
    Row(
        Modifier.fillMaxWidth().clickable(onClick = open).heightIn(min = 56.dp).padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (largeText && !status.isNullOrBlank()) AdminStatusBadge(status)
        }
        if (!largeText && !status.isNullOrBlank()) AdminStatusBadge(status)
        Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AdminStatusBadge(label: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun AdminGroup(title: String, rows: @Composable ColumnScope.() -> Unit) {
    Text(adminText(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth(), content = rows)
    }
}

internal fun adminText(text: String): String = LocaleStrings.get(text)


internal fun adminStatus(status: String): String {
    val title = status.replaceFirstChar { it.uppercase() }
    val translated = adminText(status)
    return if (translated == status) adminText(title) else translated
}
