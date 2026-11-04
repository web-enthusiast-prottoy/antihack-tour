package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.DocumentSnapshot

@Composable
internal fun DepositReview(record:DocumentSnapshot,operations:PlayerData,bangla:Boolean,close:()->Unit) {
    fun t(en:String,bn:String)=if(bangla)bn else en
    var amount by rememberSaveable(record.id){mutableStateOf("")}
    var reference by rememberSaveable(record.id){mutableStateOf("")}
    var sender by rememberSaveable(record.id){mutableStateOf("")}
    var receiver by rememberSaveable(record.id){mutableStateOf("")}
    var reason by rememberSaveable(record.id){mutableStateOf("")}
    var checked by rememberSaveable(record.id){mutableStateOf(false)}
    val paisa=depositPaisa(amount)
    AlertDialog(onDismissRequest={if(!operations.busy)close()},title={Text(t(if(BuildConfig.LOCAL_OPERATIONS)"Review test deposit" else "Review deposit",if(BuildConfig.LOCAL_OPERATIONS)"পরীক্ষামূলক জমা যাচাই" else "জমা যাচাই"))},text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(record.id,style=MaterialTheme.typography.bodySmall)
            Text(t("Claim: ","দাবি: ")+money(record.getLong("amount")?:0)+" · "+record.getString("reference"))
            Text(t("Sender: ","প্রেরক: ")+record.getString("sender").orEmpty())
            Text(t("Receiver: ","গ্রহণকারী: ")+record.getString("receiver").orEmpty())
            Text(t("Check successful receipt in the receiving bKash account, then enter the matching details. Reject discrepancies with a reason.","গ্রহণকারী বিকাশ অ্যাকাউন্টে সফল প্রাপ্তি যাচাই করে মিলে যাওয়া তথ্য দিন। অমিল হলে কারণসহ প্রত্যাখ্যান করুন।"))
            OutlinedTextField(amount,{amount=it},label={Text(t("Verified amount (taka)","যাচাই করা পরিমাণ (টাকা)"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=!operations.busy,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(reference,{reference=it},label={Text(t("Verified transfer reference","যাচাই করা লেনদেন নম্বর"))},enabled=!operations.busy,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(sender,{sender=it},label={Text(t("Verified sending number","যাচাই করা প্রেরকের নম্বর"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),enabled=!operations.busy,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(receiver,{receiver=it},label={Text(t("Verified receiving account","যাচাই করা গ্রহণকারী"))},enabled=!operations.busy,modifier=Modifier.fillMaxWidth())
            Row {Checkbox(checked,{checked=it},enabled=!operations.busy);Text(t("I checked successful receipt in the receiving account.","গ্রহণকারী অ্যাকাউন্টে সফল প্রাপ্তি যাচাই করেছি।"))}
            OutlinedTextField(reason,{reason=it},label={Text(t("Rejection or discrepancy reason","প্রত্যাখ্যান বা অমিলের কারণ"))},enabled=!operations.busy,minLines=2,isError=reason.trim().length>1000,modifier=Modifier.fillMaxWidth())
            if(operations.error.isNotBlank())Text(operations.error,color=MaterialTheme.colorScheme.error)
            TextButton(onClick={operations.operation("rejectDeposit",mapOf("id" to record.id,"reason" to reason.trim())){close()}},enabled=!operations.busy&&reason.isNotBlank()&&reason.trim().length<=1000){Text(t("Reject with reason","কারণসহ প্রত্যাখ্যান"))}
        }
    },confirmButton={Button(onClick={operations.operation("approveDeposit",mapOf("id" to record.id,"verifiedAmount" to paisa,"verifiedReference" to reference,"verifiedSender" to sender,"verifiedReceiver" to receiver,"receiptConfirmed" to checked)){close()}},enabled=!operations.busy&&checked&&paisa>0&&reference.isNotBlank()&&sender.isNotBlank()&&receiver.isNotBlank()){Text(t(if(operations.busy)"Saving…" else "Approve verified claim",if(operations.busy)"সংরক্ষণ হচ্ছে…" else "যাচাই করা দাবি অনুমোদন"))}},dismissButton={TextButton(onClick=close,enabled=!operations.busy){Text(LocaleStrings.get("Back"))}})
}

internal fun depositPaisa(text:String):Long=try{java.math.BigDecimal(text.trim()).movePointRight(2).longValueExact().takeIf{it in 1..10000000}?:0L}catch(_:Exception){0L}
internal fun validDepositSender(text:String)=Regex("01[3-9][0-9]{8}").matches(text.trim())
