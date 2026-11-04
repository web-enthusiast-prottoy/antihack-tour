package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun RosterForm(match:Tournament,captain:String,busy:Boolean,bangla:Boolean,error:String,submit:(List<String>)->Unit) {
    fun t(en:String,bn:String)=if(bangla)bn else en
    val size=rosterSize(match.mode)
    var second by rememberSaveable(match.id) {mutableStateOf("")}
    var third by rememberSaveable(match.id) {mutableStateOf("")}
    var fourth by rememberSaveable(match.id) {mutableStateOf("")}
    val names=(listOf(captain,second.trim(),third.trim(),fourth.trim())).take(size)
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(t("Review roster","দলের তালিকা যাচাই করুন"),style=MaterialTheme.typography.titleLarge)
        Text(match.title)
        Text(t("Game names will appear in the public participant list.","গেমের নাম সবার অংশগ্রহণকারী তালিকায় দেখা যাবে।"))
        Text(t("One entry slot registers this roster. Use each player's game name; do not enter phone numbers or email addresses.","একটি এন্ট্রি স্লটে এই দল নিবন্ধিত হবে। গেমের নাম দিন; ফোন নম্বর বা ইমেইল দেবেন না।"))
        Text(t("Captain: ","দলনেতা: ")+captain)
        if(size>=2)OutlinedTextField(second,{second=it},label={Text(t("Player 2 game name","খেলোয়াড় ২-এর গেমের নাম"))},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
        if(size==4) {
            OutlinedTextField(third,{third=it},label={Text(t("Player 3 game name","খেলোয়াড় ৩-এর গেমের নাম"))},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(fourth,{fourth=it},label={Text(t("Player 4 game name","খেলোয়াড় ৪-এর গেমের নাম"))},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
        }
        if(!validRoster(match.mode,names,captain))Text(t("Enter a distinct game name for every player (up to 80 characters).","প্রত্যেক খেলোয়াড়ের আলাদা গেমের নাম দিন (সর্বোচ্চ ৮০ অক্ষর)।"),color=MaterialTheme.colorScheme.error)
        if(error.isNotBlank())Text(LocaleStrings.get(error),color=MaterialTheme.colorScheme.error)
        Text(t("Entry fee: ","এন্ট্রি ফি: ")+money(match.fee))
        if(match.fee>0)Text(t("Paid entry requests need staff confirmation; a request does not reserve a place.","পেইড এন্ট্রির জন্য অ্যাডমিনের অনুমোদন প্রয়োজন; অনুরোধে আসন নিশ্চিত হয় না।"))
        Button(onClick={submit(names)},enabled=!busy&&validRoster(match.mode,names,captain),modifier=Modifier.fillMaxWidth()) {Text(t(if(busy)"Please wait…" else "Confirm roster and join",if(busy)"অপেক্ষা করুন…" else "দল নিশ্চিত করে যোগ দিন"))}
    }
}
