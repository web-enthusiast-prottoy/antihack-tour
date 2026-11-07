package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.DocumentSnapshot

@Composable
internal fun DepositReview(record:DocumentSnapshot,operations:PlayerData,bangla:Boolean,close:()->Unit) {
    fun t(en:String,bn:String)=if(bangla)bn else en
    var reason by rememberSaveable(record.id){mutableStateOf("")}
    var checked by rememberSaveable(record.id){mutableStateOf(false)}
    val paisa=record.getLong("amount")?:0L
    val reference=record.getString("reference").orEmpty()
    val sender=record.getString("sender").orEmpty()
    val receiver=record.getString("receiver").orEmpty()
    AlertDialog(onDismissRequest={if(!operations.busy)close()},title={Text(t(if(BuildConfig.LOCAL_OPERATIONS)"Review test deposit" else "Review deposit",if(BuildConfig.LOCAL_OPERATIONS)"পরীক্ষামূলক জমা যাচাই" else "জমা যাচাই"))},text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            PlayerIdentity(operations.session,record.getString("uid").orEmpty(),record.getString("username").orEmpty())
            Text(t("Claim: ","দাবি: ")+money(record.getLong("amount")?:0)+" · "+record.getString("reference"))
            Text(t("Sender: ","প্রেরক: ")+record.getString("sender").orEmpty())
            Text(t("Receiver: ","গ্রহণকারী: ")+record.getString("receiver").orEmpty())
            Text(t("Confirm that this payment was received in the bKash account shown above. Approving credits this exact amount to the player's wallet.","উপরের বিকাশ অ্যাকাউন্টে এই টাকা পেয়েছেন নিশ্চিত করুন। অনুমোদন করলে এই পরিমাণ টাকা খেলোয়াড়ের ওয়ালেটে যোগ হবে।"))
            Row {Checkbox(checked,{checked=it},enabled=!operations.busy);Text(t("I checked successful receipt in the receiving account.","গ্রহণকারী অ্যাকাউন্টে সফল প্রাপ্তি যাচাই করেছি।"))}
            OutlinedTextField(reason,{reason=it},label={Text(t("Rejection or discrepancy reason","প্রত্যাখ্যান বা অমিলের কারণ"))},enabled=!operations.busy,minLines=2,isError=reason.trim().length>1000,modifier=Modifier.fillMaxWidth())
            if(operations.error.isNotBlank())Text(operations.error,color=MaterialTheme.colorScheme.error)
            TextButton(onClick={operations.operation("rejectDeposit",mapOf("id" to record.id,"reason" to reason.trim())){close()}},enabled=!operations.busy&&reason.isNotBlank()&&reason.trim().length<=1000){Text(t("Reject with reason","কারণসহ প্রত্যাখ্যান"))}
        }
    },confirmButton={Button(onClick={operations.operation("approveDeposit",mapOf("id" to record.id,"verifiedAmount" to paisa,"verifiedReference" to reference,"verifiedSender" to sender,"verifiedReceiver" to receiver,"receiptConfirmed" to checked)){close()}},enabled=!operations.busy&&checked&&paisa>0&&reference.isNotBlank()&&sender.isNotBlank()&&receiver.isNotBlank()){Text(t(if(operations.busy)"Saving…" else "Approve deposit",if(operations.busy)"সংরক্ষণ হচ্ছে…" else "জমা অনুমোদন"))}},dismissButton={TextButton(onClick=close,enabled=!operations.busy){Text(LocaleStrings.get("Back"))}})
}
