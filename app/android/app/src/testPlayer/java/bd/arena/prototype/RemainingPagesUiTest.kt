package bd.arena.prototype

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Native component fixtures; no production accounts, payments or database writes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemainingPagesUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val now=java.time.Instant.parse("2026-11-10T18:00:00Z").toEpochMilli()
    private val match=Tournament("fixture","Evening Solo","Solo","11 Nov · 8:30 PM",32,48,fee=3000,prize=120000,startsAtMillis=now+1458000,map="Bermuda")
    private val result=ResultSummary("r1","fixture","Yesterday’s Solo","solo","Bermuda","10 Nov · 8:30 PM","NightOwl",2,5,45000,45000,"credited",2,40000,5000,true)
    private val notice=Notice("n1","Tonight’s rooms open before kickoff","Open your confirmed match to check room access.\nRoom credentials appear only after the release time.",now-60000,2)
    private val receiver=PaymentNumberState("01700000000",false,false,false,7,true)
    private val withdrawal=WithdrawalViewState(25000,10000,500,10500,true,true,false,true,false,false,"",null)

    private fun content(bangla:Boolean=false,fontScale:Float=1f,narrow:Boolean=false,body:@Composable ()->Unit) {
        LocaleStrings.configure(ui.activity,bangla)
        ui.setContent {CompositionLocalProvider(LocalDensity provides Density(ui.activity.resources.displayMetrics.density,fontScale)) {
            PlayerTheme {LocalizedTypography(bangla){Box(Modifier.fillMaxSize()) {
                Column((if(narrow)Modifier.width(320.dp)else Modifier.fillMaxWidth()).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){body()}
            }}}
        }}
    }
    private fun tap(tag:String){ui.onNodeWithTag(tag).performScrollTo().performClick()}
    private fun type(tag:String,value:String){ui.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)}
    private fun fits(text:String) {
        val out=mutableListOf<TextLayoutResult>()
        ui.onAllNodesWithText(text,useUnmergedTree=true).onFirst().performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(out)}
        assertTrue(out.isNotEmpty())
        out.forEach{r->assertFalse("Height clipped: $text",r.didOverflowHeight);repeat(r.lineCount){i->
            assertFalse("Truncated: $text",r.isLineEllipsized(i));assertTrue("Width clipped: $text",r.getLineLeft(i)>=-1f&&r.getLineRight(i)<=r.size.width+1f)
        }}
    }
    private fun capture(name:String) {
        ui.waitForIdle();val file=File(System.getProperty("java.io.tmpdir"),"antihack-remaining-ui/$name.png");file.parentFile!!.mkdirs()
        ui.runOnIdle{val v=ui.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));file.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    }
    @Composable private fun MatchCard(entry:String?="confirmed",value:Tournament=match,open:()->Unit={},room:()->Unit={}) {
        MyMatchCard(value,entry,now,open,room){m->Image(painterResource(modeArtwork(value.mode)),null,m,contentScale=ContentScale.Crop)}
    }

    @Test fun myMatchesCardSeparatesDetailsAndRoomActions() {
        var details=0;var rooms=0
        content{ScreenHeader("My matches");MatchSectionTabs(false,{},{}) ;MatchCard(open={details++},room={rooms++})}
        tap("match-card-fixture");assertEquals(1,details);assertEquals(0,rooms)
        tap("my-room-fixture");assertEquals(1,rooms)
        capture("my-matches")
    }
    @Test fun unconfirmedAndCancelledEntriesCannotOpenRoomShortcut() {
        var entry by mutableStateOf<String?>("requested");var value by mutableStateOf(match);var calls=0
        content{MatchCard(entry,value,room={calls++})}
        tap("my-room-fixture");ui.onNodeWithTag("my-room-fixture").assertIsNotEnabled()
        ui.runOnIdle{entry="confirmed";value=match.copy(matchStatus="cancelled")}
        tap("my-room-fixture");assertEquals(0,calls)
    }
    @Test fun upcomingResultsTabsExposeBothCallbacks() {
        var selected by mutableStateOf(false)
        content{MatchSectionTabs(selected,{selected=false},{selected=true})}
        tap("my-results");ui.onNodeWithTag("my-results").assertIsSelected()
        tap("my-upcoming");assertFalse(selected)
    }
    @Test fun announcedAwardDoesNotImplyCreditBeforeSettlement() {
        var model by mutableStateOf(result.copy(settlement="pending",credited=0));var opened=0
        content{ResultCard(model){opened++}}
        ui.onNodeWithText("৳450").assertExists();ui.onNodeWithText("Not credited · awaiting settlement").assertExists()
        tap("result-r1");assertEquals(1,opened)
        ui.runOnIdle{model=result.copy(credited=42550)}
        ui.onNodeWithText("৳425.50 · Credited to winnings").assertExists()
        ui.onNodeWithText("Not credited · awaiting settlement").assertDoesNotExist()
        capture("results")
    }
    @Test fun resultBreakdownPreservesExactComponentsAndPendingKillState() {
        content{ResultBreakdown(result.copy(killsFinal=false,credited=40000))}
        listOf("৳400","৳50","৳450","Kill rewards pending","NightOwl","Match ID · fixture").forEach{ui.onAllNodesWithText(it).onFirst().assertExists()}
        capture("reward-breakdown")
    }
    @Test fun pendingResultDoesNotShowInventedRewardOrCredit() {
        content{PendingResultCard(match,"10 Nov · 8:30 PM")}
        ui.onNodeWithText("Results pending").assertExists();ui.onNodeWithText("৳450").assertDoesNotExist()
    }
    @Test fun walletUsesBothBalancesExcludesReserveAndHasBothActions() {
        var add=0;var withdraw=0
        content{ScreenHeader("Wallet");WalletBalances(60000,25000,10000,false,{add++},{withdraw++});WalletActivityHeader("All activity",false,{},{})
            WalletActivityItem(ActivitySummary("reward","Match reward","+৳250","completed","11 Nov · 7:45 PM",true)){}
            WalletActivityItem(ActivitySummary("deposit","Add money","+৳500","completed","11 Nov · 6:12 PM",true)){}
        }
        listOf("৳600","৳250","৳850","৳100","Balance","Winnings").forEach{ui.onNodeWithText(it).assertExists()}
        ui.onNodeWithText("৳600.00").assertDoesNotExist()
        tap("wallet-add");tap("wallet-withdraw");assertEquals(1,add);assertEquals(1,withdraw)
        capture("wallet")
    }
    @Test fun cachedOrZeroWinningsDisableWithdrawalButLeaveDepositReachable() {
        var cached by mutableStateOf(true);var winnings by mutableStateOf(25000L);var add=0;var withdraw=0
        content{WalletBalances(60000,winnings,0,cached,{add++},{withdraw++})}
        ui.onNodeWithTag("wallet-withdraw").assertIsNotEnabled()
        tap("wallet-add");assertEquals(1,add)
        ui.runOnIdle{cached=false;winnings=0}
        tap("wallet-withdraw");assertEquals(0,withdraw)
    }
    @Test fun balancesReactToObservedUpdatesAndRetainNonzeroFractionalPaisa() {
        var balance by mutableStateOf(60000L);var winnings by mutableStateOf(25000L)
        content{WalletBalances(balance,winnings,0,false,{},{})}
        ui.runOnIdle{balance=10050;winnings=2075}
        listOf("৳100.50","৳20.75","৳121.25").forEach{ui.onNodeWithText(it).assertExists()}
        ui.onNodeWithText("৳850").assertDoesNotExist()
    }
    @Test fun activityRowOpensItsOwnDetailsAndShowsActualPendingStatus() {
        var selected=""
        content{WalletActivityItem(ActivitySummary("tx1","Add money","৳500","pending","11 Nov · 8:30 PM",false)){selected="tx1"}}
        tap("activity-tx1");assertEquals("tx1",selected)
        ui.onNodeWithText("+৳500").assertDoesNotExist();ui.onNodeWithText("11 Nov · 8:30 PM · pending").assertExists()
        capture("activity")
    }
    @Test fun activityFiltersCoverStoredLedgerKindsAndUnknownTypesRemainInAll() {
        LocaleStrings.configure(ui.activity,false)
        assertTrue(walletActivityMatches("entry fee","Match entry"));assertTrue(walletActivityMatches("entry_fee","Match entry"))
        assertTrue(walletActivityMatches("prize","Match reward"));assertTrue(walletActivityMatches("Deposit","Deposit"))
        assertTrue(walletActivityMatches("Withdrawal","Withdrawal"));assertTrue(walletActivityMatches("refund","All activity"))
        assertFalse(walletActivityMatches("entry fee","Match reward"))
    }
    @Test fun sameDocumentIdFromDifferentLedgersKeepsIndependentActions() {
        var selected=""
        val one=ActivitySummary("same-id","Add money","+৳500","completed","11 Nov",true,key="ledger/same-id")
        val two=one.copy(amount="+৳250",key="walletLedger/same-id")
        content{WalletActivityItem(one){selected=one.key};WalletActivityItem(two){selected=two.key}}
        tap("activity-walletLedger/same-id");assertEquals(two.key,selected)
        tap("activity-ledger/same-id");assertEquals(one.key,selected)
    }
    @Test fun transactionDetailsPreserveBothReferencesWithoutDuplicatingAmount() {
        val row=ActivitySummary("REQUEST-123","Add money","৳500","pending","11 Nov 2026 · 8:30 PM",false,reference="PROVIDER-456")
        content{WalletActivityDetails(row)}
        ui.onAllNodesWithText("৳500").assertCountEquals(1)
        ui.onNodeWithText("Transaction ID: REQUEST-123").assertExists();ui.onNodeWithText("Payment reference: PROVIDER-456").assertExists()
        ui.onNodeWithText(row.time).assertExists();capture("transaction-details")
    }
    @Test fun largeBanglaActivityToolbarWrapsAndCallsRefreshAndFilters() {
        var filter by mutableStateOf("All activity");var refreshes=0
        content(bangla=true,fontScale=2f,narrow=true){WalletActivityHeader(filter,false,{filter=it},{refreshes++})}
        fits(LocaleStrings.get("Activity"));fits(LocaleStrings.get("All activity"));tap("activity-refresh");assertEquals(1,refreshes)
        tap("activity-filter")
        ui.onNode(hasText(LocaleStrings.get("Withdrawal")) and hasAnyAncestor(isPopup())).performClick();assertEquals("Withdrawal",filter)
    }
    @Test fun depositValidationBlocksInvalidAmountSenderAndReference() {
        var amount by mutableStateOf("");var reference by mutableStateOf("");var sender by mutableStateOf("");var calls=0
        content{DepositContent(receiver,amount,reference,sender,false,"",{amount=it},{reference=it},{sender=it}){calls++}}
        ui.onNodeWithTag("deposit-submit").assertIsNotEnabled()
        type("deposit-amount","100000.01");type("deposit-reference","ABC12345");type("deposit-sender","01700000000")
        ui.onNodeWithTag("deposit-submit").assertIsNotEnabled()
        type("deposit-amount","500.50");type("deposit-reference","?");ui.onNodeWithTag("deposit-submit").assertIsNotEnabled()
        type("deposit-reference","ABC12345");type("deposit-sender","0170");ui.onNodeWithTag("deposit-submit").assertIsNotEnabled()
        type("deposit-sender","01700000000");tap("deposit-submit");assertEquals(1,calls)
    }
    @Test fun depositKeyboardAndBusyStatePreventDuplicateCallbacks() {
        var amount by mutableStateOf("");var reference by mutableStateOf("");var sender by mutableStateOf("");var busy by mutableStateOf(false);var calls=0
        content{DepositContent(receiver,amount,reference,sender,busy,"",{amount=it},{reference=it},{sender=it}){calls++;busy=true}}
        type("deposit-amount","500");ui.onNodeWithTag("deposit-amount").performImeAction();ui.onNodeWithTag("deposit-reference").assertIsFocused()
        type("deposit-reference","AB123456");ui.onNodeWithTag("deposit-reference").performImeAction();ui.onNodeWithTag("deposit-sender").assertIsFocused()
        type("deposit-sender","01700000000");ui.onNodeWithTag("deposit-sender").performImeAction()
        assertEquals(1,calls);ui.onNodeWithTag("deposit-amount").assertIsNotEnabled();tap("deposit-submit");assertEquals(1,calls)
    }
    @Test fun unverifiedReceivingNumberCannotBeCopiedOrSubmitted() {
        var receiving by mutableStateOf(receiver.copy(cached=true));var calls=0
        content{DepositContent(receiving,"500","ABC12345","01700000000",false,"",{},{},{}){calls++}}
        ui.onNodeWithTag("deposit-copy").assertIsNotEnabled();ui.onNodeWithTag("deposit-submit").assertIsNotEnabled()
        ui.runOnIdle{receiving=receiver}
        tap("deposit-copy")
        val clip=ui.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(receiver.number,clip.primaryClip!!.getItemAt(0).text.toString())
        tap("deposit-submit");assertEquals(1,calls)
        capture("add-money")
    }
    @Test fun depositServerErrorKeepsEnteredPaymentValuesAvailable() {
        content{DepositContent(receiver,"500","ABC12345","01700000000",false,"Connection unavailable. Retry to check the saved result.",{},{},{}){}}
        ui.onNodeWithTag("deposit-reference").assertTextContains("ABC12345");ui.onNodeWithText("Connection unavailable. Retry to check the saved result.").assertExists()
    }
    @Test fun disabledDepositDoesNotExposeAnEnabledSubmissionAction() {
        content{DepositContent(receiver.copy(enabled=false),"500","ABC12345","01700000000",false,"",{},{},{}){}}
        ui.onNodeWithTag("deposit-submit").assertDoesNotExist();ui.onNodeWithText("Deposits are not enabled. Contact the admin.").assertExists()
    }
    @Test fun withdrawalReviewShowsPayoutFeeAndReserveSeparately() {
        var reviewed=false
        content{WithdrawalContent(withdrawal,"100","01700000000",{},{},{reviewed=true},{},{},{},{})}
        tap("withdraw-review");assertTrue(reviewed);capture("withdrawal")
    }
    @Test fun withdrawalConfirmationIsDisabledWhenBalanceOrPolicyIsUnverified() {
        var state by mutableStateOf(withdrawal.copy(review=true,loading=true));var sent=0
        content{WithdrawalContent(state,"100","01700000000",{},{},{},{},{sent++},{},{})}
        ui.onNodeWithTag("withdraw-confirm").assertIsNotEnabled()
        ui.runOnIdle{state=state.copy(loading=false,enabled=false)};ui.onNodeWithTag("withdraw-confirm").assertIsNotEnabled()
        ui.runOnIdle{state=state.copy(enabled=true,amountValid=false)};ui.onNodeWithTag("withdraw-confirm").assertIsNotEnabled()
        ui.runOnIdle{state=withdrawal.copy(review=true)}
        tap("withdraw-confirm");assertEquals(1,sent)
        listOf("৳100","৳5","৳105","01700000000").forEach{ui.onNodeWithText(it).assertExists()}
        capture("withdrawal-review")
    }
    @Test fun withdrawalBusyStateBlocksConfirmationAndBack() {
        var sent=0;var back=0
        content{WithdrawalContent(withdrawal.copy(review=true,busy=true),"100","01700000000",{},{},{},{back++},{sent++},{},{})}
        ui.onNodeWithTag("withdraw-confirm").assertIsNotEnabled();ui.onNodeWithText("Back").assertIsNotEnabled();assertEquals(0,sent+back)
    }
    @Test fun withdrawalReceiptDoesNotClaimPayoutForReservedFunds() {
        var done=0
        content{WithdrawalContent(withdrawal.copy(receipt=WithdrawalReceipt("wd1","pending",10000)),"100","01700000000",{},{},{},{},{},{},{done++})}
        ui.onNodeWithText("Funds reserved. No payout sent yet.").assertExists();tap("withdraw-done");assertEquals(1,done)
        capture("withdrawal-saved")
    }
    @Test fun paymentOutcomeKeepsActualServerMessageAndRequestId() {
        val message="Request saved. Awaiting admin approval.\nREQUEST-123"
        var done=0
        content{PaymentOutcome(message){done++}}
        ui.onNodeWithText(message).assertExists();tap("payment-done");assertEquals(1,done)
    }
    @Test fun profileShowsCurrentIdentityWithoutAddingPlayerAvatars() {
        var edits=0
        content{ScreenHeader("Profile");ProfileSummary("player@example.com","NightOwl","123456789",false){edits++};ProfileLinks(false,true,true,{},{},{},{})}
        ui.onNodeWithText("FF UID · 123456789").assertExists();tap("profile-edit");assertEquals(1,edits)
        capture("profile")
    }
    @Test fun profileKeyboardSavesActualValuesOnceAndDisablesBusyForm() {
        var busy by mutableStateOf(false);var sent=listOf<String>();var calls=0
        content{PlayerProfileEditor("owner","Old name","123",busy,"",{name,uid->sent=listOf(name,uid);calls++;busy=true},{})}
        type("profile-name","New game name");ui.onNodeWithTag("profile-name").performImeAction();ui.onNodeWithTag("profile-uid").assertIsFocused()
        type("profile-uid","987654321");ui.onNodeWithTag("profile-uid").performImeAction()
        assertEquals(listOf("New game name","987654321"),sent);ui.onNodeWithTag("profile-save").assertIsNotEnabled();tap("profile-save");assertEquals(1,calls)
    }
    @Test fun profileLengthValidationAndOptionalFieldsRetainExistingContract() {
        var calls=0
        content{PlayerProfileEditor("owner","","",false,"",{_,_->calls++},{})}
        ui.onNodeWithTag("profile-save").assertIsEnabled()
        type("profile-name","n".repeat(81));ui.onNodeWithTag("profile-save").assertIsNotEnabled()
        type("profile-name","");type("profile-uid","1".repeat(41));ui.onNodeWithTag("profile-save").assertIsNotEnabled()
        type("profile-uid","");tap("profile-save");assertEquals(1,calls)
    }
    @Test fun profileValuesSurviveRestorationAndServerFailure() {
        LocaleStrings.configure(ui.activity,false);val restore=StateRestorationTester(ui)
        restore.setContent{PlayerTheme{Column(Modifier.verticalScroll(rememberScrollState())){PlayerProfileEditor("owner","Old","123",false,"Profile could not be saved. Retry setup; your account already exists.",{_,_->},{})}}}
        type("profile-name","Restored name");type("profile-uid","456");restore.emulateSavedInstanceStateRestore()
        ui.onNodeWithTag("profile-name").assertTextContains("Restored name");ui.onNodeWithTag("profile-uid").assertTextContains("456")
    }
    @Test fun languageSheetCallsBothLanguageOptions() {
        var selected by mutableStateOf(false)
        content{LanguageChoices(selected){selected=it}}
        tap("language-true");assertTrue(selected);ui.onNodeWithTag("language-true").assertIsSelected();tap("language-false");assertFalse(selected)
    }
    @Test fun signOutConfirmationProvidesCancelAndExplicitSignOut() {
        var signs=0;var cancels=0
        content{SignOutConfirmation({signs++},{cancels++})}
        tap("signout-cancel");assertEquals(0,signs);assertEquals(1,cancels)
        tap("signout-confirm");assertEquals(1,signs)
    }
    @Test fun banglaProfileLinksAreLocalizedAndGuestScopeStaysPublic() {
        var signedIn by mutableStateOf(true);var contacts=0;var languages=0
        content(bangla=true){ProfileLinks(true,signedIn,true,{contacts++},{},{languages++},{})}
        ui.onNodeWithText("যোগাযোগ").performClick();assertEquals(1,contacts)
        ui.onNodeWithText("ভাষা").performClick();assertEquals(1,languages)
        ui.runOnIdle{signedIn=false}
        ui.onNodeWithText("যোগাযোগ").assertDoesNotExist();ui.onNodeWithText(LocaleStrings.get("Sign out")).assertDoesNotExist()
        ui.onNodeWithText("ভাষা").assertExists()
    }
    @Test fun notificationPreviewOpensCurrentRevisionAndExposesUnreadState() {
        var opened:Notice?=null;var unread by mutableStateOf(true)
        content{ScreenHeader("Notifications");NotificationItem(notice,unread){opened=notice;unread=false}}
        ui.onNodeWithTag("notice-n1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Unread"))
        tap("notice-n1");assertEquals("n1:2",opened!!.readKey)
        ui.onNodeWithTag("notice-n1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"All read"))
        capture("notifications")
    }
    @Test fun announcementReaderPreservesFullTitleBodyAndTimestamp() {
        val long=notice.copy(body=notice.body+"\n"+"Long saved paragraph. ".repeat(100))
        content{AnnouncementReader(long)}
        ui.onNodeWithText(long.body).assertExists();fits(long.body);capture("announcement-reader")
    }
    @Test fun noticeGroupsUseDhakaMidnightAndNeverFabricateMissingDates() {
        LocaleStrings.configure(ui.activity,false)
        val boundary=java.time.Instant.parse("2026-11-10T18:00:00Z").toEpochMilli()
        assertEquals("Today",noticeDateGroup(notice.copy(updatedAt=boundary),boundary,false))
        assertEquals("Yesterday",noticeDateGroup(notice.copy(updatedAt=boundary-1),boundary,false))
        assertEquals("Announcements",noticeDateGroup(notice.copy(updatedAt=0),boundary,false))
    }
    @Test fun notificationCacheErrorAndRetryRemainExplicit() {
        var calls=0
        content{NotificationFeedStatus(NoticeFeed(listOf(notice),false,true,true)){calls++}}
        ui.onNodeWithText("Could not refresh announcements.").assertExists();ui.onNodeWithText("Retry").performClick();assertEquals(1,calls)
    }
    @Test fun emptyNotificationsDoNotShowSampleAnnouncements() {
        content{NotificationFeedStatus(NoticeFeed(loading=false)){}}
        ui.onNodeWithText("No notifications yet.").assertExists();ui.onNodeWithText(notice.title).assertDoesNotExist()
    }
    @Test fun informationPagePreservesPublishedTextAndLinkAction() {
        var calls=0;val body="Published operator contact information.\n\nA second saved paragraph."
        content{ScreenHeader("Contact");InformationContent("Contact the team",body,"example.com",false){calls++}}
        ui.onNodeWithText(body).assertExists();tap("information-link");assertEquals(1,calls)
        capture("information")
    }
    @Test fun informationWithoutLinkDoesNotAddAnActionAndLinkErrorsRemainVisible() {
        var host by mutableStateOf<String?>(null)
        content{InformationContent("Privacy policy","Saved policy body",host,true){}}
        ui.onNodeWithTag("information-link").assertDoesNotExist()
        ui.runOnIdle{host="example.com"}
        ui.onNodeWithText("Could not open the link on this device.").assertExists()
    }
    @Test fun narrowDoubleTextWalletRemainsReadableAndWithdrawReachable() {
        content(fontScale=2f,narrow=true){WalletBalances(60000,25000,10000,false,{},{})}
        listOf("৳600","৳250","৳850","৳100").forEach{fits(it)}
        ui.onNodeWithTag("wallet-withdraw").performScrollTo().assertIsDisplayed();capture("wallet-large-text")
    }
    @Test fun narrowBanglaDoubleTextMatchAndResultsKeepTheirActionsAndAmounts() {
        var results by mutableStateOf(false)
        content(bangla=true,fontScale=2f,narrow=true){if(results)ResultCard(result){}else MatchCard(room={results=true})}
        fits(match.title);ui.onNodeWithTag("my-room-fixture").performScrollTo().assertIsDisplayed();tap("my-room-fixture")
        fits("৳450");fits(LocaleStrings.get("Award"));fits(result.title)
        capture("results-bangla-large-text")
    }
    @Test fun narrowBanglaReaderRetainsFullLongText() {
        val body="সংরক্ষিত তথ্য। ".repeat(100)
        var reader by mutableStateOf(false)
        content(bangla=true,fontScale=2f,narrow=true){if(reader)AnnouncementReader(notice.copy(body=body))else NotificationItem(notice,true){reader=true}}
        tap("notice-n1");fits(body)
    }
    @Test fun narrowBanglaInformationKeepsLongBodyAndLinkReachable() {
        val body="প্রকাশিত গোপনীয়তা নীতি। ".repeat(100);var opened=0
        content(bangla=true,fontScale=2f,narrow=true){InformationContent("গোপনীয়তা নীতি",body,"example.com",false){opened++}}
        fits(body);ui.onNodeWithTag("information-link").performScrollTo().assertIsDisplayed();tap("information-link");assertEquals(1,opened)
    }
    @Test fun banglaDoubleTextPaymentAndProfileControlsFitNarrowPhone() {
        var profile by mutableStateOf(false)
        content(bangla=true,fontScale=2f,narrow=true){if(profile)PlayerProfileEditor("owner","নতুন নাম","123456789",false,"",{_,_->},{})else DepositContent(receiver,"500","ABCD1234","01700000000",false,"",{},{},{}){profile=true}}
        fits(receiver.number);ui.onNodeWithTag("deposit-submit").performScrollTo().assertIsDisplayed();tap("deposit-submit")
        ui.onNodeWithTag("profile-save").performScrollTo().assertIsDisplayed()
        fits(LocaleStrings.get("Free Fire UID (optional)"));capture("profile-bangla-large-text")
    }
    @Test @Config(qualifiers="w800dp-h360dp-xhdpi") fun banglaDoubleTextWithdrawalReviewAndSignOutStayReachableInLandscape() {
        var signed=false
        content(bangla=true,fontScale=2f){WithdrawalContent(withdrawal.copy(review=true),"100","01700000000",{},{},{},{},{signed=true},{},{})}
        ui.onNodeWithTag("withdraw-confirm").performScrollTo().assertIsDisplayed();tap("withdraw-confirm");assertTrue(signed)
    }
}
