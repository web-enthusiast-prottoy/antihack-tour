package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdminSupport(session: AdminSession, bangla: Boolean, back: () -> Unit) {
    fun t(en: String, bn: String) = if (bangla) bn else en
    SupportScreen(session, bangla, back) { data, record, done ->
        var response by rememberSaveable(record.id){mutableStateOf("")}
        var status by rememberSaveable(record.id){mutableStateOf("review")}
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(response,{response=it},label={Text(t("Reply (up to 4000 characters)","উত্তর (সর্বোচ্চ ৪০০০ অক্ষর)"))},modifier=Modifier.fillMaxWidth(),minLines=3,enabled=!data.busy,isError=response.trim().length>4000)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("open","review","resolved").forEach{value->FilterChip(status==value,{status=value},label={Text(supportStatus(value,bangla))},enabled=!data.busy)}}
            Button(onClick={data.reply(record,response.trim(),status,done)},enabled=!data.busy&&response.isNotBlank()&&response.trim().length<=4000){Text(t(if(data.busy)"Saving…" else "Save reply and status",if(data.busy)"সংরক্ষণ হচ্ছে…" else "উত্তর ও অবস্থা সংরক্ষণ"))}
        }
    }
}
