package bd.arena.prototype

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun RosterForm(match:Tournament,captain:String,busy:Boolean,bangla:Boolean,error:String,balance:Long?=null,canJoin:Boolean=true,
    artwork:@Composable (Modifier)->Unit={modifier->Image(painterResource(modeArtwork(match.image)),null,modifier,contentScale=ContentScale.Crop)},submit:(List<String>)->Unit) {
    fun t(en:String,bn:String)=LocaleStrings.get(en,if(bangla)bn else en)
    val size=rosterSize(match.mode)
    var second by rememberSaveable(match.id){mutableStateOf("")}
    var third by rememberSaveable(match.id){mutableStateOf("")}
    var fourth by rememberSaveable(match.id){mutableStateOf("")}
    val focus=remember(match.id){List(3){FocusRequester()}}
    val names=listOf(captain,second.trim(),third.trim(),fourth.trim()).take(size)
    val valid=validRoster(match.mode,names,captain)
    fun confirm(){if(!busy&&canJoin&&valid)submit(names)}
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(t("Review entry","এন্ট্রি যাচাই করুন"),style=MaterialTheme.typography.titleLarge)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            artwork(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(match.title,style=MaterialTheme.typography.titleMedium)
                Text(listOf(LocaleStrings.get(match.mode.lowercase()),match.map,match.time).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(size>1) {
            Text(t("Game names will appear in the public participant list.","গেমের নাম সবার অংশগ্রহণকারী তালিকায় দেখা যাবে।"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            val values=listOf(second,third,fourth)
            val change=listOf<(String)->Unit>({second=it},{third=it},{fourth=it})
            repeat(size-1){index->
                val last=index==size-2
                OutlinedTextField(values[index],change[index],label={Text(t("Player ${index+2} game name","খেলোয়াড় ${index+2}-এর গেমের নাম"))},
                    singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("join-player-${index+2}").focusRequester(focus[index]),
                    keyboardOptions=KeyboardOptions(imeAction=if(last)ImeAction.Done else ImeAction.Next),
                    keyboardActions=KeyboardActions(onNext={focus[index+1].requestFocus()},onDone={confirm()}))
            }
            if(!valid&&values.take(size-1).any{it.isNotEmpty()})Text(t("Enter a distinct game name for every player (up to 80 characters).","প্রত্যেক খেলোয়াড়ের আলাদা গেমের নাম দিন (সর্বোচ্চ ৮০ অক্ষর)।"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
        }
        Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant,contentColor=MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                LabeledValue(t("Entry fee","এন্ট্রি ফি"),money(match.fee).removeSuffix(".00"))
                if(match.fee>0&&balance!=null&&balance>=match.fee)LabeledValue(t("Balance after entry","এন্ট্রির পর ব্যালেন্স"),money(balance-match.fee).removeSuffix(".00"))
            }
        }
        if(match.fee>0)Text(t("Entry uses balance first, then available winnings.","এন্ট্রিতে আগে ব্যালেন্স, পরে ব্যবহারযোগ্য জয়ী অর্থ কাটা হবে।"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(error.isNotBlank())Text(LocaleStrings.get(error),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
        if(!canJoin)Text(t("Match availability changed. Close this sheet and refresh before joining.","ম্যাচের অবস্থা বদলেছে। যোগ দেওয়ার আগে এই অংশ বন্ধ করে হালনাগাদ করুন।"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick={confirm()},enabled=!busy&&canJoin&&valid,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("join-confirm")) {
            if(busy)CircularProgressIndicator(Modifier.padding(end=8.dp).size(18.dp),strokeWidth=2.dp)
            Text(if(busy)t("Confirming entry…","এন্ট্রি নিশ্চিত হচ্ছে…") else t("Confirm & join","নিশ্চিত করে যোগ দিন")+if(match.fee>0)" · "+money(match.fee).removeSuffix(".00") else "")
        }
    }
}
