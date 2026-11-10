package bd.arena.prototype

import org.junit.Assert.*
import org.junit.Test

class AnnouncementNotificationsTest {
    private val data=mapOf("type" to "announcement","noticeId" to "sample","title" to "Sample title","body" to "Sample body","revision" to "2")
    @Test fun newAndUpdatedAnnouncementsNotifyOncePerRevision() {
        assertEquals(2L,announcementRevision(data,0))
        assertEquals(2L,announcementRevision(data,1))
        assertNull(announcementRevision(data,2))
        assertNull(announcementRevision(data,3))
    }
    @Test fun historyIsSilentAndServerTimestampPrecisionIsPreserved() {
        val enrolled=com.google.firebase.Timestamp(100L,10)
        assertFalse(announcementAfterEnrollment(com.google.firebase.Timestamp(99L,999999999),enrolled))
        assertFalse(announcementAfterEnrollment(enrolled,enrolled))
        assertTrue(announcementAfterEnrollment(com.google.firebase.Timestamp(100L,11),enrolled))
    }
    @Test fun malformedAndUnrelatedMessagesAreIgnored() {
        for(patch in listOf(mapOf("type" to "other"),mapOf("noticeId" to ""),mapOf("title" to ""),mapOf("body" to ""),mapOf("revision" to "0"),mapOf("revision" to "overflow99999999999999999")))assertNull(announcementRevision(data+patch,0))
    }
}
