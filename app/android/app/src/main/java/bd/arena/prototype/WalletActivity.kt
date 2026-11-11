package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity

internal data class ActivitySummary(val id:String,val title:String,val amount:String,val status:String,val time:String,val positive:Boolean,
    val key:String=id,val reference:String="",val referenceLabel:String="Payment reference: ")

internal fun activityTitle(type:String)=LocaleStrings.get(when(type.lowercase().replace('_',' ')) {
    "deposit"->"Add money";"prize","placement reward","kill reward","reward"->"Match reward";"entry","entry fee","match entry","join"->"Match entry";else->type.replaceFirstChar{it.uppercase()}
})
internal fun walletActivityMatches(type:String,filter:String)=filter=="All activity"||type.equals(filter,true)||activityTitle(type)==LocaleStrings.get(filter)

@Composable
internal fun WalletActivityHeader(filter:String,loading:Boolean,choose:(String)->Unit,refresh:()->Unit) {
    var menu by remember{mutableStateOf(false)}
    @Composable fun Controls(modifier:Modifier) {
        Row(modifier) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick={menu=true},modifier=Modifier.testTag("activity-filter")){Text(LocaleStrings.get(filter))}
                DropdownMenu(menu,onDismissRequest={menu=false}) {
                    listOf("All activity","Deposit","Match entry","Match reward","Withdrawal").forEach{choice->DropdownMenuItem(text={Text(LocaleStrings.get(choice))},onClick={choose(choice);menu=false})}
                }
            }
            TextButton(onClick=refresh,enabled=!loading,modifier=Modifier.testTag("activity-refresh")){Text(LocaleStrings.get("Refresh"))}
        }
    }
    if(LocalDensity.current.fontScale>1.3f)Column {
        Text(LocaleStrings.get("Activity"),style=MaterialTheme.typography.titleLarge)
        Controls(Modifier.fillMaxWidth())
    }else Row(verticalAlignment=Alignment.CenterVertically) {
        Text(LocaleStrings.get("Activity"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
        Controls(Modifier.weight(2f))
    }
}

@Composable
internal fun WalletActivityItem(row:ActivitySummary,open:()->Unit) {
    Surface(onClick=open,color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().testTag("activity-${row.key}")) {
        Row(Modifier.padding(vertical=14.dp,horizontal=4.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(color=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.primary,shape=RoundedCornerShape(12.dp)) {
                Box(Modifier.size(44.dp),contentAlignment=Alignment.Center){Icon(painterResource(when(row.title){LocaleStrings.get("Add money")->R.drawable.ic_plus;LocaleStrings.get("Match reward")->R.drawable.ic_matches;LocaleStrings.get("Withdrawal")->R.drawable.ic_arrow_up;LocaleStrings.get("Match entry")->R.drawable.ic_arrow_down;else->R.drawable.ic_wallet}),null,Modifier.size(22.dp))}
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(row.title,style=MaterialTheme.typography.titleSmall)
                Text(listOf(row.time,LocaleStrings.get(row.status)).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(row.amount,Modifier.weight(.55f),style=MaterialTheme.typography.titleSmall,color=if(row.positive)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun WalletActivityDetails(row:ActivitySummary) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(LocaleStrings.get("Transaction details"),style=MaterialTheme.typography.titleLarge)
        Text(row.amount,style=MaterialTheme.typography.headlineLarge)
        Text(LocaleStrings.get(row.status),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        LabeledValue(LocaleStrings.get("Type"),row.title)
        LabeledValue(LocaleStrings.get("Time"),row.time)
        SelectionContainer {Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(LocaleStrings.get("Transaction ID: ").trimEnd()+" "+row.id,style=MaterialTheme.typography.bodyMedium)
            if(row.reference.isNotBlank())Text(LocaleStrings.get(row.referenceLabel).trimEnd()+" "+row.reference,style=MaterialTheme.typography.bodyMedium)
        }}
    }
}
