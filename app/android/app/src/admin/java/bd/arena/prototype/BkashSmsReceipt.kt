package bd.arena.prototype

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

internal const val bkashSmsAddress = "25274"
internal const val receiptWindowMillis = smsReviewWindowMillis

internal data class BkashSmsReceipt(
    val amountPaisa: Long,
    val sender: String?,
    val reference: String,
    val paidAtMillis: Long
)

private val amount = """((?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)\.[0-9]{2})"""
private val mobile = """(\+?8801[3-9][0-9]{8}|01[3-9][0-9]{8})"""
private val received = Regex("""You have received Tk $amount from $mobile\.(?: Ref [^\r\n]*?\.)? Fee Tk """)
private val cashIn = Regex("""Cash In Tk $amount from $mobile successful\. Fee Tk """)
private val bank = Regex("""You have received deposit from iBanking of Tk $amount from [^\r\n]+?\. Fee Tk """)
private val receiptEnd = Regex(
    """[0-9,.]+\. Balance Tk [0-9,.]+\. TrxID ([A-Za-z0-9-]{6,80}) at (\d{1,2}/\d{1,2}/(?:\d{4}|\d{2}) \d{1,2}:\d{2})\.?(?: Download App: https[^\s]+)?"""
)
private val receiptDate = DateTimeFormatter.ofPattern("d/M/uuuu H:mm", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)

private val shortReceiptDate = DateTimeFormatterBuilder().appendPattern("d/M/")
    .appendValueReduced(ChronoField.YEAR, 2, 2, 2000).appendPattern(" H:mm")
    .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

internal fun isBkashSmsAddress(address: String) = address.trim() == bkashSmsAddress || address.trim().equals("bKash", true)

internal fun parseBkashSms(address: String, body: String): BkashSmsReceipt? {
    if (!isBkashSmsAddress(address) || body.length > 2000) return null
    val text = body.replace('\u00a0', ' ').trim().replace(Regex("\\s+"), " ")
    val header = sequenceOf(received, cashIn, bank).firstNotNullOfOrNull { it.find(text)?.takeIf { match -> match.range.first == 0 } }
        ?: return null
    val footer = receiptEnd.matchEntire(text.substring(header.range.last + 1)) ?: return null
    val paisa = depositPaisa(header.groupValues[1].replace(",", ""))
    if (paisa == 0L) return null
    val paidAt = runCatching {
        LocalDateTime.parse(footer.groupValues[2], if(footer.groupValues[2].substringBefore(" ").substringAfterLast("/").length==2)shortReceiptDate else receiptDate)
            .atZone(ZoneId.of("Asia/Dhaka")).toInstant().toEpochMilli()
    }.getOrNull() ?: return null
    val sender = header.groupValues.getOrNull(2)?.let { if(it.startsWith("+880"))"0"+it.drop(4) else if(it.startsWith("880"))"0"+it.drop(3) else it }
    return BkashSmsReceipt(paisa, sender, footer.groupValues[1].uppercase(Locale.ROOT), paidAt)
}
