package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AdminWorkspace(items:List<Pair<Int,String>>,selected:Int,choose:(Int)->Unit,drawer:DrawerState,content:@Composable ()->Unit) {
    val navigation:@Composable ColumnScope.()->Unit={
        BrandLogo(Modifier.fillMaxWidth().height(120.dp).padding(16.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            items.forEach{(id,label)->NavigationDrawerItem(label={Text(LocaleStrings.get(label))},selected=selected==id,onClick={choose(id)},modifier=Modifier.padding(horizontal=12.dp))}
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if(maxWidth>=840.dp)Row(Modifier.fillMaxSize()) {
            Surface(Modifier.width(240.dp).fillMaxHeight(),color=MaterialTheme.colorScheme.surface) {Column(content=navigation)}
            Box(Modifier.weight(1f),contentAlignment=androidx.compose.ui.Alignment.TopCenter){content()}
        } else ModalNavigationDrawer(drawerState=drawer,drawerContent={ModalDrawerSheet(Modifier.widthIn(max=320.dp),content=navigation)},content=content)
    }
}
