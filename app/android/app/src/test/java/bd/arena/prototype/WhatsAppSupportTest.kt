package bd.arena.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WhatsAppSupportTest {
    @Test fun supportLinkUsesInternationalDigitsWithoutSendingAMessage() {
        for(number in listOf("01703883397","8801703883397","+8801703883397"))assertEquals("https://wa.me/8801703883397",supportChatUrl(number))
        assertEquals("https://wa.me/8801812345678",supportChatUrl("01812345678"))
    }
    @Test fun invalidNumbersCannotInjectAnExternalLinkOrMessage() {
        for(number in listOf("","0170388339","01203883397","01703883397?text=hello","https://example.com","01703883397/other"))assertThrows(IllegalArgumentException::class.java){supportChatUrl(number)}
    }
}
