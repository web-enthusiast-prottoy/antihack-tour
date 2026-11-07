package bd.arena.prototype

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class BkashSmsReceiptTest {
    private val cashIn = "Cash In Tk 2,000.00 from 01800000000 successful. Fee Tk 0.00. Balance Tk 2,279.70. TrxID SAMPLECASH1 at 20/10/2026 15:39. Download App: https://bKa.sh/8app"
    private val received = "You have received Tk 1,000.00 from 01600000000. Ref sample. Fee Tk 0.00. Balance Tk 1,639.19. TrxID SAMPLERECV2 at 07/10/2026 12:55"
    private val bank = "You have received deposit from iBanking of Tk 1,500.00 from City Bank. Fee Tk 0.00. Balance Tk 1,742.20. TrxID SAMPLEBANK3 at 15/10/2026 20:32"

    @Test fun parsesSuccessfulMobileReceiptsWithExactPaisa() {
        val receipt = checkNotNull(parseBkashSms("25274", cashIn))
        assertEquals(200000L, receipt.amountPaisa)
        assertEquals("01800000000", receipt.sender)
        assertEquals("SAMPLECASH1", receipt.reference)
        assertEquals(Instant.parse("2026-10-20T09:39:00Z").toEpochMilli(), receipt.paidAtMillis)
        assertEquals("SAMPLERECV2", parseBkashSms("25274", received)?.reference)
        assertEquals(100000L, parseBkashSms("25274", received.replace(" Ref sample.", ""))?.amountPaisa)
    }

    @Test fun bankReceiptHasNoMobileNumberToMatch() {
        val receipt = checkNotNull(parseBkashSms("25274", bank))
        assertEquals(150000L, receipt.amountPaisa)
        assertNull(receipt.sender)
    }

    @Test fun rejectsWrongSenderInvalidDateAndMalformedAmounts() {
        assertNull(parseBkashSms("025274", cashIn))
        assertNull(parseBkashSms("25274", cashIn.replace("2,000.00", "20,00.00")))
        assertNull(parseBkashSms("25274", cashIn.replace("20/10/2026", "31/02/2026")))
        assertNull(parseBkashSms("25274", cashIn.replace("successful", "failed")))
    }
    @Test fun oldReceiptCannotMatchNewClaimEvenWithAnOldPhoneClock() {
        val receipt = checkNotNull(parseBkashSms("25274", cashIn))
        val now = Instant.parse("2026-11-05T06:00:00Z").toEpochMilli()
        val receipts = mapOf(receipt.reference to listOf(receipt))
        fun match(clock: Long) = matchingSmsReceipt("01800000001", "01800000001", receipt.reference,
            receipt.sender, receipt.amountPaisa, now, receipts, clock)
        assertNull(match(now))
        assertNull(match(receipt.paidAtMillis))
    }

    @Test fun requiresExactUnambiguousRecentReceipt() {
        val receipt = checkNotNull(parseBkashSms("25274", cashIn))
        val now = receipt.paidAtMillis + 60_000
        fun match(rows: List<BkashSmsReceipt>, sender: String? = receipt.sender) =
            matchingSmsReceipt("01800000001", "01800000001", receipt.reference, sender,
                receipt.amountPaisa, now, mapOf(receipt.reference to rows), now)
        assertEquals(receipt, match(listOf(receipt)))
        assertNull(match(listOf(receipt), "01700000000"))
        assertNull(match(listOf(receipt, receipt.copy(amountPaisa = 1))))
        assertNull(match(listOf(receipt.copy(sender = null)), null))
    }

    @Test fun acceptsBrandedSenderAndNormalMessageFormatting() {
        assertEquals("SAMPLECASH1", parseBkashSms("bKash", cashIn)?.reference)
        assertEquals("SAMPLECASH1", parseBkashSms(" BKASH ", cashIn)?.reference)
        assertEquals(200000L, parseBkashSms("bKash", cashIn.replace(" Fee Tk", "\nFee Tk").replace("Tk ", "Tk\u00a0"))?.amountPaisa)
        assertEquals("01800000000", parseBkashSms("bKash", cashIn.replace("01800000000", "+8801800000000"))?.sender)
        assertEquals(parseBkashSms("25274", cashIn)?.paidAtMillis, parseBkashSms("bKash", cashIn.replace("20/10/2026", "20/10/26"))?.paidAtMillis)
        assertNull(parseBkashSms("Fake bKash", cashIn))
        assertNull(parseBkashSms("01700000000", cashIn))
    }

    @Test fun notificationFallbackRequiresOfficialPackageAndCompleteUnambiguousReceipt() {
        val receipt=checkNotNull(parseBkashSms("bKash",cashIn))
        assertEquals(receipt,parseBkashNotification(bkashAppPackage,listOf("bKash",cashIn,cashIn)))
        assertNull(parseBkashNotification("com.example.fake",listOf(cashIn)))
        assertNull(parseBkashNotification(bkashAppPackage,listOf("You received Tk 2,000.00")))
        assertNull(parseBkashNotification(bkashAppPackage,listOf(cashIn.replace("01800000000","018*****000"))))
        assertNull(parseBkashNotification(bkashAppPackage,listOf(cashIn,cashIn.replace("2,000.00","1,000.00"))))
        assertNull(parseBkashNotification(bkashAppPackage,listOf(cashIn.replace("Cash In", "Send Money"))))
    }
    @Test fun mismatchDiagnosticsExplainTheHoldWithoutLeakingReceiptDetails() {
        val receipt=checkNotNull(parseBkashSms("bKash",cashIn))
        val now=receipt.paidAtMillis+60_000
        val receipts=mapOf(receipt.reference to listOf(receipt,receipt))
        assertNull(receiptMismatch("01800000001","01800000001",receipt.reference,receipt.sender,receipt.amountPaisa,now,receipts,now))
        assertEquals("Amount does not match",receiptMismatch("01800000001","01800000001",receipt.reference,receipt.sender,1,now,receipts,now))
        assertEquals("Sending number does not match",receiptMismatch("01800000001","01800000001",receipt.reference,"01700000000",receipt.amountPaisa,now,receipts,now))
        val report=ReceiptCheckReport(1,ReceiptScan(listOf(receipt),1,1,0),0,mapOf("Amount does not match" to 1),listOf("Owner access required."))
        assertTrue(report.summary().contains("Amount does not match: 1"))
        assertTrue(report.summary().contains("Owner access required."))
        assertFalse(report.summary().contains(receipt.reference))
        assertFalse(report.summary().contains(receipt.sender!!))
    }

    @Test fun ownerReportedReceivedMessageWorksWithEitherSenderLabel() {
        // Same message structure as the report, with personal details replaced.
        val body="You have received Tk 10.00 from 01600000000. Fee Tk 0.00. Balance Tk 1,710.70. TrxID SAMPLEMSG7 at 06/11/2026 22:28"
        val receipt=checkNotNull(parseBkashSms("bKash",body))
        assertEquals(1000L,receipt.amountPaisa)
        assertEquals("01600000000",receipt.sender)
        assertEquals("SAMPLEMSG7",receipt.reference)
        assertEquals(receipt,parseBkashSms("25274",body))
    }

}
