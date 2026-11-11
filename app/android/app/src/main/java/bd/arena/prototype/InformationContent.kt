package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun InformationContent(title:String,body:String,host:String?,linkFailed:Boolean,open:()->Unit) {
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            SelectionContainer {Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(title,style=MaterialTheme.typography.headlineSmall)
                Text(body,style=MaterialTheme.typography.bodyLarge)
            }}
            if(host!=null) {
                Text(host,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick=open,modifier=Modifier.fillMaxWidth().testTag("information-link")){Text(LocaleStrings.get("Open contact or information link"))}
                if(linkFailed)Text(LocaleStrings.get("Could not open the link on this device."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            }
        }
    }
}
