package bd.arena.prototype
import org.junit.Assert.*
import org.junit.Test
class PageLinksTest {
    @Test fun acceptsHttpsContactLinks() {assertTrue(validPageLink("https://t.me/example"));assertTrue(validPageLink("https://example.com/help?lang=bn"))}
    @Test fun rejectsUnsafeSchemesMalformedUrlsAndEmbeddedLogins() {
        for(url in listOf("http://example.com","javascript:alert(1)","file:///tmp/x","https://","https://user:secret@example.com","https://example.com/help me",""))assertFalse(url,validPageLink(url))
    }
}
