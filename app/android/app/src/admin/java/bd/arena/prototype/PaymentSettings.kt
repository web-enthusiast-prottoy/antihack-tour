package bd.arena.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue

private data class PaymentChange(val number: String, val enabled: Boolean, val expectedRevision: Long)

@Composable
internal fun PaymentSettings(session: AdminSession, bangla: Boolean, back: () -> Unit, smsSettings: @Composable () -> Unit) {
    fun t(en: String, bn: String) = if (bangla) bn else en
    BackHandler { back() }
    val current = paymentNumber(session)
    var number by remember(current.number, current.revision) { mutableStateOf(if (current.enabled) current.number else "") }
    var enabled by remember(current.enabled, current.revision) { mutableStateOf(current.enabled) }
    var busy by remember { mutableStateOf(false) }
    var change by remember { mutableStateOf<PaymentChange?>(null) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val validNumber = validDepositSender(number)
    val connected = !current.loading && !current.cached && !current.error
    val enabledLabel = t("Enable deposit instructions", "টাকা জমার নির্দেশনা চালু করুন")

    fun save(reviewed: PaymentChange) {
        val actor = session.uid ?: return
        if (!session.allowed || busy) return
        change = null
        busy = true
        error = ""
        message = ""
        val ref = session.db.document("paymentInstructions/bkash")
        session.db.runTransaction { tx ->
            val old = tx.get(ref)
            require((old.getLong("revision") ?: 0) == reviewed.expectedRevision)
            val revision = reviewed.expectedRevision + 1
            tx.set(ref, mapOf(
                "number" to reviewed.number,
                "enabled" to reviewed.enabled,
                "revision" to revision,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            tx.set(session.db.document("auditRecords/payment_bkash_$revision"), mapOf(
                "action" to "savePaymentInstructions", "path" to ref.path,
                "actor" to actor, "at" to FieldValue.serverTimestamp()
            ))
        }.addOnCompleteListener {
            if (session.uid == actor && session.allowed) {
                busy = false
                if (it.isSuccessful) message = t("Receiving number saved.", "টাকা গ্রহণের নম্বর সংরক্ষিত হয়েছে।")
                else error = t("Could not save. Reconnect and reload before retrying.", "সংরক্ষণ হয়নি। সংযোগ দিয়ে আবার লোড করে চেষ্টা করুন।")
            }
        }
    }

    Column(
        Modifier.widthIn(max = 640.dp).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(adminText("Payment settings"), back = back)
        if (current.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(
            number, { number = it; message = "" }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(t("Receiving bKash number", "টাকা গ্রহণের বিকাশ নম্বর")) },
            enabled = connected && !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = number.isNotBlank() && !validNumber
        )
        if (number.isNotBlank() && !validNumber) {
            Text(adminText("Valid bKash number required"), color = MaterialTheme.colorScheme.error)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(enabledLabel, Modifier.weight(1f))
            Switch(enabled, { enabled = it; message = "" }, enabled = connected && !busy, modifier = Modifier.semantics { contentDescription = enabledLabel })
        }
        if (!current.loading && (current.cached || current.error)) {
            Text(t("Connect to load and update the current number.", "বর্তমান নম্বর দেখতে ও বদলাতে ইন্টারনেট সংযোগ দিন।"))
        }
        if (message.isNotEmpty()) Text(message)
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = { change = PaymentChange(number.trim(), enabled, current.revision) },
            enabled = session.allowed && !busy && connected &&
                ((!enabled && number.isBlank()) || validNumber)
        ) { Text(t(if (busy) "Saving…" else "Save number", if (busy) "সংরক্ষণ হচ্ছে…" else "নম্বর সংরক্ষণ করুন")) }
        smsSettings()
    }

    change?.let { reviewed ->
        AlertDialog(
            onDismissRequest = { change = null },
            title = { Text(adminText("Confirm payment settings")) },
            text = {
                if (reviewed.enabled) LabeledValue(adminText("Receiving number"), reviewed.number)
                else Text(adminText("Deposit instructions will be hidden."))
            },
            dismissButton = { TextButton(onClick = { change = null }) { Text(adminText("Cancel")) } },
            confirmButton = { TextButton(onClick = { save(reviewed) }) { Text(adminText("Save")) } }
        )
    }
}
