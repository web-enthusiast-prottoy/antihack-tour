package bd.arena.prototype
import org.junit.Assert.assertEquals
import org.junit.Test
class NoticeTest {
    private val notice = Notice("one", "Title", "Message", 0, 1)
    @Test fun unreadCountExcludesReadRevision() { assertEquals(0, unreadNoticeCount(listOf(notice), setOf("one:1"))) }
    @Test fun revisedNoticeBecomesUnread() { assertEquals(1, unreadNoticeCount(listOf(notice.copy(revision = 2)), setOf("one:1"))) }
    @Test fun emptyFeedHasNoUnreadItems() { assertEquals(0, unreadNoticeCount(emptyList(), emptySet())) }
}
