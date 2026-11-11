package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role

@Composable
internal fun WalletBalances(balance:Long,winnings:Long,reserved:Long,cached:Boolean,add:()->Unit,withdraw:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                @Composable fun Balance(label:String,value:Long,note:String,modifier:Modifier) {
                    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(LocaleStrings.get(label),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(compactMoney(value),style=MaterialTheme.typography.headlineLarge.copy(fontWeight=FontWeight.SemiBold),color=if(label=="Winnings")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(LocaleStrings.get(note),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(LocalDensity.current.fontScale>1.3f)Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Balance("Balance",balance,"Added money",Modifier.fillMaxWidth());Balance("Winnings",winnings,"Available winnings",Modifier.fillMaxWidth())
                } else Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {Balance("Balance",balance,"Added money",Modifier.weight(1f));Balance("Winnings",winnings,"Available winnings",Modifier.weight(1f))}
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                LabeledValue(LocaleStrings.get("Available to join"),compactMoney(balance+winnings))
                if(cached)Text(LocaleStrings.get("Saved balance · reconnect to refresh"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                @Composable fun Add(modifier:Modifier) {Button(onClick=add,modifier=modifier.heightIn(min=48.dp).testTag("wallet-add")){Text(LocaleStrings.get("Add money"))}}
                @Composable fun Withdraw(modifier:Modifier) {OutlinedButton(onClick=withdraw,enabled=winnings>0&&!cached,modifier=modifier.heightIn(min=48.dp).testTag("wallet-withdraw")){Text(LocaleStrings.get("Withdraw"))}}
                if(LocalDensity.current.fontScale>1.3f)Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Add(Modifier.fillMaxWidth());Withdraw(Modifier.fillMaxWidth())}
                else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Add(Modifier.weight(1f));Withdraw(Modifier.weight(1f))}
            }
        }
        if(reserved>0)Surface(color=MaterialTheme.colorScheme.surfaceVariant,contentColor=MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(LocaleStrings.get("Reserved for withdrawal"),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(compactMoney(reserved),style=MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
internal fun WalletBalanceInfo(balance:Long,winnings:Long,reserved:Long) {
    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text(LocaleStrings.get("Your balances"),style=MaterialTheme.typography.titleLarge)
        LabeledValue(LocaleStrings.get("Balance"),compactMoney(balance))
        LabeledValue(LocaleStrings.get("Available winnings"),compactMoney(winnings))
        LabeledValue(LocaleStrings.get("Reserved for withdrawal"),compactMoney(reserved))
        Text(LocaleStrings.get("Match entry uses deposits first, then available winnings. Only winnings can be withdrawn. Reserved funds cannot be spent while a withdrawal is under review."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ProfileSummary(email:String,username:String,uid:String,saved:Boolean,editEnabled:Boolean=true,edit:()->Unit) {
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(email,style=MaterialTheme.typography.titleLarge)
            Text(username.ifBlank{LocaleStrings.get("Game username not added")},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(uid.isNotBlank())Text("FF UID · $uid",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick=edit,enabled=editEnabled,modifier=Modifier.fillMaxWidth().testTag("profile-edit")){Text(LocaleStrings.get("Edit profile"))}
            if(saved)Text(LocaleStrings.get("Profile saved."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun LanguageChoices(bangla:Boolean,choose:(Boolean)->Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(LocaleStrings.get("Language"),style=MaterialTheme.typography.titleLarge)
        listOf(false to "English",true to "বাংলা").forEach{(value,label)->
            Surface(onClick={choose(value)},color=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().testTag("language-$value").semantics{selected=bangla==value;role=Role.RadioButton}) {
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected=bangla==value,onClick=null);Text(label,Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun SignOutConfirmation(confirm:()->Unit,cancel:()->Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(LocaleStrings.get("Sign out?"),style=MaterialTheme.typography.titleLarge)
        Text(LocaleStrings.get("You can keep browsing public matches."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick=confirm,modifier=Modifier.fillMaxWidth().testTag("signout-confirm")){Text(LocaleStrings.get("Sign out"))}
        OutlinedButton(onClick=cancel,modifier=Modifier.fillMaxWidth().testTag("signout-cancel")){Text(LocaleStrings.get("Stay signed in"))}
    }
}

@Composable
internal fun PlayerProfileEditor(owner:String,initialName:String,initialUid:String,busy:Boolean,error:String,save:(String,String)->Unit,cancel:(()->Unit)?) {
    var name by rememberSaveable(owner){mutableStateOf(initialName)}
    var uid by rememberSaveable(owner){mutableStateOf(initialUid)}
    val next=remember{FocusRequester()}
    val valid=name.length<=80&&uid.length<=40
    fun send(){if(!busy&&valid)save(name,uid)}
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(name,{name=it},label={Text(LocaleStrings.get("Free Fire username"))},singleLine=true,isError=name.length>80,
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next),keyboardActions=KeyboardActions(onNext={next.requestFocus()}),enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("profile-name"))
        OutlinedTextField(uid,{uid=it},label={Text(LocaleStrings.get("Free Fire UID (optional)"))},singleLine=true,isError=uid.length>40,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={send()}),enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("profile-uid").focusRequester(next))
        if(!valid)Text(LocaleStrings.get("Check your profile fields."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
        if(error.isNotBlank())Text(LocaleStrings.get(error),color=MaterialTheme.colorScheme.error)
        Button(onClick={send()},enabled=!busy&&valid,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("profile-save")) {
            if(busy)CircularProgressIndicator(Modifier.padding(end=8.dp).size(18.dp),strokeWidth=2.dp)
            Text(LocaleStrings.get(if(busy)"Saving…" else "Save profile"))
        }
        if(cancel!=null)OutlinedButton(onClick=cancel,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(LocaleStrings.get("Cancel"))}
    }
}

@Composable
internal fun ProfileActionRow(label:String,enabled:Boolean=true,value:String="",icon:Int?=null,open:()->Unit) {
    Surface(onClick=open,enabled=enabled,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min=56.dp).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            if(icon!=null)Icon(painterResource(icon),null,Modifier.padding(end=12.dp).size(24.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
            if(value.isNotBlank())Text(value,Modifier.padding(horizontal=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(painterResource(R.drawable.ic_chevron),null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ProfileLinks(bangla:Boolean,signedIn:Boolean,supportEnabled:Boolean,contact:()->Unit,support:()->Unit,language:()->Unit,signOut:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(16.dp)) {
            Column {
                if(signedIn) {
                    ProfileActionRow(LocaleStrings.get("Contact"),icon=R.drawable.ic_chat,open=contact)
                    HorizontalDivider(Modifier.padding(horizontal=16.dp),color=MaterialTheme.colorScheme.outlineVariant)
                }
                ProfileActionRow(LocaleStrings.get("Support · WhatsApp"),enabled=supportEnabled,icon=R.drawable.ic_chat,open=support)
            }
        }
        ProfileActionRow(LocaleStrings.get("Language"),value=if(bangla)"বাংলা" else "English",icon=R.drawable.ic_language,open=language)
        if(signedIn)OutlinedButton(onClick=signOut,modifier=Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_logout),null,Modifier.padding(end=8.dp).size(20.dp));Text(LocaleStrings.get("Sign out"))
        }
    }
}
