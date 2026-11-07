package bd.arena.prototype

internal fun depositPaisa(text:String):Long=try{java.math.BigDecimal(text.trim()).movePointRight(2).longValueExact().takeIf{it in 1..10000000}?:0L}catch(_:Exception){0L}
internal fun validDepositSender(text:String)=Regex("01[3-9][0-9]{8}").matches(text.trim())

internal const val smsReviewWindowMillis = 7 * 24 * 60 * 60 * 1000L

internal fun validSmsReviewTimes(createdAt: Long, paidAt: Long, now: Long): Boolean =
    createdAt > 0 && paidAt > 0 &&
        createdAt in (now - smsReviewWindowMillis)..(now + 60_000) &&
        paidAt in (now - smsReviewWindowMillis)..(now + 60_000) &&
        paidAt in (createdAt - smsReviewWindowMillis)..(createdAt + 60_000)
