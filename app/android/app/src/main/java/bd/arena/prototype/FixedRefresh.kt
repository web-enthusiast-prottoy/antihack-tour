package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal class RefreshAction(val enabled:Boolean,val run:()->Unit)
internal val LocalFixedRefresh=staticCompositionLocalOf<MutableMap<Any,RefreshAction>?>{null}

@Composable
internal fun RegisterRefresh(enabled:Boolean=true,refresh:()->Unit) {
    val actions=LocalFixedRefresh.current
    if(actions==null){IconButton(onClick=refresh,enabled=enabled){Icon(painterResource(R.drawable.ic_refresh),LocaleStrings.get("Refresh"))};return}
    val id=remember{Any()}
    val current by rememberUpdatedState(refresh)
    DisposableEffect(actions,enabled){actions?.set(id,RefreshAction(enabled){current()});onDispose{actions?.remove(id)}}
}

@Composable
internal fun FixedRefreshHost(content:@Composable ()->Unit) {
    val actions=remember{mutableStateMapOf<Any,RefreshAction>()}
    CompositionLocalProvider(LocalFixedRefresh provides actions) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(bottom=if(actions.isEmpty())0.dp else 76.dp)){content()}
            if(actions.isNotEmpty())FilledIconButton(onClick={actions.values.toList().filter{it.enabled}.forEach{it.run()}},enabled=actions.values.any{it.enabled},modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp).size(52.dp).testTag("fixed-refresh")) {
                Icon(painterResource(R.drawable.ic_refresh),LocaleStrings.get("Refresh"))
            }
        }
    }
}
