package bd.arena.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal data class WithdrawalReceipt(val id:String,val status:String,val amount:Long)
internal data class WithdrawalViewState(val available:Long,val minimum:Long,val fee:Long,val total:Long?,val amountValid:Boolean,val numberValid:Boolean,
    val loading:Boolean,val enabled:Boolean,val busy:Boolean,val review:Boolean,val error:String,val receipt:WithdrawalReceipt?)

@Composable
internal fun WithdrawalContent(state:WithdrawalViewState,amount:String,number:String,changeAmount:(String)->Unit,changeNumber:(String)->Unit,
    review:()->Unit,back:()->Unit,confirm:()->Unit,reload:()->Unit,done:()->Unit) {
    fun t(text:String)=LocaleStrings.get(text)
    val focus=remember{FocusRequester()}
    val keyboard=LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(t(if(state.receipt!=null)"Request saved" else if(state.review)"Review withdrawal" else "Withdraw"),style=MaterialTheme.typography.headlineSmall)
        if(BuildConfig.LOCAL_OPERATIONS)Text(t("Local sample only. No real money is transferred."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        val receipt=state.receipt
        if(receipt!=null) {
            Text(compactMoney(receipt.amount),style=MaterialTheme.typography.displaySmall)
            Text(t(receipt.status),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            Text(t(if(receipt.status in listOf("pending","approved"))"Funds reserved. No payout sent yet." else if(receipt.status=="sent")"Payout sent." else "Check this request in Activity."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(t("Request ID")+" · "+receipt.id,style=MaterialTheme.typography.labelSmall)
            Button(onClick=done,modifier=Modifier.fillMaxWidth().testTag("withdraw-done")){Text(t("Back to wallet"))}
        } else if(state.review) {
            Surface(color=MaterialTheme.colorScheme.surfaceVariant,contentColor=MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(t("Payout amount"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(compactMoney(runCatching{awardPaisa(amount)}.getOrDefault(0)),style=MaterialTheme.typography.displaySmall)
                }
            }
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(t("Payout bKash number"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(number,style=MaterialTheme.typography.titleLarge)
            }
            LabeledValue(t("Withdrawal fee"),compactMoney(state.fee))
            LabeledValue(t("Total reserved"),compactMoney(state.total?:0))
            Text(t("Check the number. Funds stay reserved during review."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            WithdrawalError(state.error)
            Button(onClick=confirm,enabled=!state.loading&&state.enabled&&state.amountValid&&state.numberValid&&!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("withdraw-confirm")) {
                if(state.busy)CircularProgressIndicator(Modifier.padding(end=8.dp).size(18.dp),strokeWidth=2.dp)
                Text(t(if(state.busy)"Please wait…" else "Confirm request"))
            }
            OutlinedButton(onClick=back,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text(t("Back"))}
        } else {
            Surface(color=MaterialTheme.colorScheme.surfaceVariant,contentColor=MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(t("Available winnings"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if(state.loading)t("Please wait…")else compactMoney(state.available),style=MaterialTheme.typography.headlineLarge)
                }
            }
            if(!state.enabled&&!state.loading)Text(t("New withdrawals are not enabled. Existing requests appear in Transaction history."),style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(amount,changeAmount,label={Text(t("Withdrawal amount (taka)"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Next),keyboardActions=KeyboardActions(onNext={focus.requestFocus()}),singleLine=true,
                isError=amount.isNotBlank()&&!state.amountValid,supportingText=if(amount.isNotBlank()&&!state.amountValid)({Text(t("Use up to two decimals, meet the minimum, and include the fee within your winnings."))})else null,
                enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("withdraw-amount"))
            OutlinedTextField(number,changeNumber,label={Text(t("Payout bKash number"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={keyboard?.hide()}),singleLine=true,
                isError=number.isNotBlank()&&!state.numberValid,supportingText=if(number.isNotBlank()&&!state.numberValid)({Text(t("Enter an 11-digit bKash number."))})else null,
                enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("withdraw-number").focusRequester(focus))
            Text(t("Minimum withdrawal")+" · "+compactMoney(state.minimum)+"   "+t("Withdrawal fee")+" · "+compactMoney(state.fee),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            WithdrawalError(state.error)
            Button(onClick=review,enabled=!state.loading&&state.enabled&&state.amountValid&&state.numberValid&&!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("withdraw-review")){Text(t("Review withdrawal"))}
            TextButton(onClick=reload,enabled=!state.busy){Text(t("Reload"))}
        }
    }
}
