package bd.arena.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue

@Composable
internal fun SupportSettings(session:AdminSession,bangla:Boolean,back:()->Unit) {
    fun t(en:String,bn:String)=if(bangla)bn else en
    BackHandler{back()}
    var refresh by remember{mutableStateOf(0)}
    val current=supportNumber(session,refresh)
    var number by remember(current.number,current.revision){mutableStateOf(current.number)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var saved by remember{mutableStateOf(false)}
    val valid=runCatching{supportChatUrl(number)}.isSuccess
    RegisterRefresh(!busy){refresh++}
    Column(Modifier.widthIn(max=640.dp).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        ScreenHeader(t("WhatsApp support","হোয়াটসঅ্যাপ সহায়তা"),back=back)
        Text(t("Players can open a WhatsApp chat from Profile → Support.","খেলোয়াড়রা প্রোফাইল → সহায়তা থেকে হোয়াটসঅ্যাপ চ্যাট খুলতে পারবেন।"))
        if(current.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(number,{number=it;saved=false;error=""},label={Text(t("WhatsApp number","হোয়াটসঅ্যাপ নম্বর"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),isError=number.isNotBlank()&&!valid,enabled=!busy&&!current.loading,modifier=Modifier.fillMaxWidth())
        if(!valid)Text(t("Enter a valid Bangladesh mobile number.","সঠিক বাংলাদেশি মোবাইল নম্বর দিন।"),color=MaterialTheme.colorScheme.error)
        if(current.cached||current.error)Text(t("Connect to load and update the current number.","বর্তমান নম্বর দেখতে ও বদলাতে ইন্টারনেট সংযোগ দিন।"))
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        if(saved)Text(t("Support number saved.","সহায়তার নম্বর সংরক্ষিত হয়েছে।"))
        Button(onClick={
            val actor=session.uid?:return@Button
            val reviewed=number.trim().removePrefix("+88").removePrefix("88")
            val expected=current.revision
            busy=true;error="";saved=false
            val ref=session.db.document("supportSettings/whatsapp")
            session.db.runTransaction{tx->
                val old=tx.get(ref)
                require((old.getLong("revision")?:0)==expected){"The support number changed. Refresh and retry."}
                tx.set(ref,mapOf("number" to reviewed,"revision" to expected+1,"updatedAt" to FieldValue.serverTimestamp()))
                tx.set(session.db.document("auditRecords/support_whatsapp_${expected+1}"),mapOf("action" to "saveSupportSettings","path" to ref.path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
            }.addOnCompleteListener{if(session.uid==actor&&session.allowed){busy=false;saved=it.isSuccessful;if(!it.isSuccessful)error=t("Could not save. Refresh and retry.","সংরক্ষণ হয়নি। রিফ্রেশ করে আবার চেষ্টা করুন।")}}
        },enabled=session.allowed&&valid&&!busy&&!current.loading&&!current.cached&&!current.error,modifier=Modifier.fillMaxWidth()){Text(t(if(busy)"Saving…" else "Save number",if(busy)"সংরক্ষণ হচ্ছে…" else "নম্বর সংরক্ষণ করুন"))}
    }
}
