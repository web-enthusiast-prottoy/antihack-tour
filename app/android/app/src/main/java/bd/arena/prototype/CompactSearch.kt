package bd.arena.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun CompactSearch(value:String,change:(String)->Unit,label:String,modifier:Modifier=Modifier,submit:()->Unit={}) {
    val keyboard=LocalSoftwareKeyboardController.current
    BasicTextField(value,change,singleLine=true,textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide();submit()}),modifier=modifier.fillMaxWidth().semantics{contentDescription=label},decorationBox={field->
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(14.dp)).heightIn(min=48.dp).padding(start=14.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_search),null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f).padding(vertical=10.dp)) {
                if(value.isEmpty())Text(label,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                field()
            }
            if(value.isNotEmpty())IconButton(onClick={change("")}){Icon(painterResource(R.drawable.ic_close),LocaleStrings.get("Clear search"))}else Spacer(Modifier.width(10.dp))
        }
    })
}
