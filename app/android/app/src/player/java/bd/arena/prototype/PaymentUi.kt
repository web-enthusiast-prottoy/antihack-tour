package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp

@Composable
internal fun DepositContent(receiving:PaymentNumberState,amount:String,reference:String,sender:String,busy:Boolean,error:String,
    changeAmount:(String)->Unit,changeReference:(String)->Unit,changeSender:(String)->Unit,submit:()->Unit) {
    fun t(text:String)=LocaleStrings.get(text)
    val clipboard=LocalClipboardManager.current
    val focus=remember{List(3){FocusRequester()}}
    var copied by remember(receiving.number){mutableStateOf(false)}
    LaunchedEffect(copied){if(copied){kotlinx.coroutines.delay(2000);copied=false}}
    val validReference=Regex("[A-Za-z0-9-]{6,80}").matches(reference.trim())
    val allowed=!busy&&receiving.enabled&&!receiving.loading&&!receiving.cached&&!receiving.error&&depositPaisa(amount)>0&&validDepositSender(sender)&&validReference
    fun send(){if(allowed)submit()}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(t("Add money"),style=MaterialTheme.typography.headlineSmall)
        if(receiving.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(receiving.enabled) {
            OutlinedTextField(amount,changeAmount,label={Text(t("Amount sent (৳)"))},singleLine=true,enabled=!busy,isError=amount.isNotBlank()&&depositPaisa(amount)==0L,
                supportingText=if(amount.isNotBlank()&&depositPaisa(amount)==0L)({Text(t("Enter a valid amount, up to ৳100,000."))})else null,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Next),keyboardActions=KeyboardActions(onNext={focus[1].requestFocus()}),modifier=Modifier.fillMaxWidth().testTag("deposit-amount").focusRequester(focus[0]))
            OutlinedTextField(reference,changeReference,label={Text(t("Transaction ID"))},singleLine=true,enabled=!busy,isError=reference.isNotBlank()&&!validReference,
                supportingText=if(reference.isNotBlank()&&!validReference)({Text(t("Check the transaction ID from your payment."))})else null,
                keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Characters,imeAction=ImeAction.Next),keyboardActions=KeyboardActions(onNext={focus[2].requestFocus()}),modifier=Modifier.fillMaxWidth().testTag("deposit-reference").focusRequester(focus[1]))
            OutlinedTextField(sender,changeSender,label={Text(t("Sender’s mobile number"))},singleLine=true,enabled=!busy,isError=sender.isNotBlank()&&!validDepositSender(sender),
                supportingText=if(sender.isNotBlank()&&!validDepositSender(sender))({Text(t("Enter the 11-digit number you sent from."))})else null,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={send()}),modifier=Modifier.fillMaxWidth().testTag("deposit-sender").focusRequester(focus[2]))
            Surface(color=MaterialTheme.colorScheme.surfaceVariant,contentColor=MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(t("Send money to bKash first"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    @Composable fun Copy() {TextButton(onClick={clipboard.setText(AnnotatedString(receiving.number));copied=true},enabled=!receiving.loading&&!receiving.cached&&!receiving.error,modifier=Modifier.testTag("deposit-copy")){Text(t(if(copied)"Number copied" else "Copy number"))}}
                    if(LocalDensity.current.fontScale>1.3f)Column {Text(receiving.number,style=MaterialTheme.typography.headlineSmall);Copy()}
                    else Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(receiving.number,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);Copy()
                    }
                }
            }
            Text(t("Balance updates after payment approval."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else if(!receiving.loading&&!receiving.error)Text(t("Deposits are not enabled. Contact the admin."),style=MaterialTheme.typography.bodyMedium)
        if(receiving.cached||receiving.error)Text(t("Reconnect to confirm the receiving number."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(BuildConfig.LOCAL_OPERATIONS)Text(t("Test version · do not send real money."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(error.isNotBlank())Text(t(error),color=MaterialTheme.colorScheme.error)
        if(receiving.enabled)Button(onClick={send()},enabled=allowed,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("deposit-submit")) {
            if(busy)CircularProgressIndicator(Modifier.padding(end=8.dp).size(18.dp),strokeWidth=2.dp)
            Text(t(if(busy)"Submitting…" else "Submit request"))
        }
    }
}

@Composable
internal fun PaymentOutcome(message:String,done:()->Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text(LocaleStrings.get("Payment update"),style=MaterialTheme.typography.titleLarge)
        Text(message,style=MaterialTheme.typography.bodyMedium)
        Button(onClick=done,modifier=Modifier.fillMaxWidth().testTag("payment-done")){Text(LocaleStrings.get("Back to wallet"))}
    }
}
