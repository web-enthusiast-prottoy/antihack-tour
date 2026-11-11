package bd.arena.prototype

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsActions
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

/** Component fixtures verify the native UI, not a live Firebase connection. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w360dp-h800dp-xhdpi",application=ComposeTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MatchDetailsJoinUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val now=java.time.Instant.parse("2026-11-10T18:00:00Z").toEpochMilli()
    private val match=Tournament("details-101","Evening Solo","Solo","11 Nov · 8:30 PM",32,48,fee=3000,prize=120000,
        rules="Use your registered game name. Join on time and follow the saved match rules.",startsAtMillis=now+1_458_000,map="Bermuda",artworkId="saved-art")
    private fun content(bangla:Boolean=false,fontScale:Float=1f,body:@Composable ()->Unit) {
        LocaleStrings.configure(ui.activity,bangla)
        ui.setContent {CompositionLocalProvider(LocalDensity provides Density(ui.activity.resources.displayMetrics.density,fontScale)) {
            PlayerTheme {LocalizedTypography(bangla){Box(Modifier.fillMaxSize()){body()}}}
        }}
    }
    @Composable private fun Hero(value:Tournament=match) {
        MatchDetailsHero(value,now){m->Image(painterResource(modeArtwork(value.image)),null,m,contentScale=ContentScale.Crop)}
    }
    private fun tap(tag:String){ui.onNodeWithTag(tag).performScrollTo().performClick()}
    private fun type(player:Int,value:String){ui.onNodeWithTag("join-player-$player").performScrollTo().performTextReplacement(value)}
    private fun assertFits(text:String) {
        val layouts=mutableListOf<TextLayoutResult>()
        ui.onNodeWithText(text,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertTrue(layouts.isNotEmpty())
        layouts.forEach{r->
            assertFalse("Height clipped: $text",r.didOverflowHeight)
            repeat(r.lineCount){i->
                assertFalse("Truncated: $text",r.isLineEllipsized(i))
                assertTrue("Width clipped: $text",r.getLineLeft(i)>=-1f&&r.getLineRight(i)<=r.size.width+1f)
                assertTrue("Bottom clipped: $text",r.getLineBottom(i)<=r.size.height+1f)
            }
        }
    }
    private fun capture(name:String) {
        ui.waitForIdle()
        val file=File(System.getProperty("java.io.tmpdir"),"antihack-details-ui/$name.png");file.parentFile!!.mkdirs()
        ui.runOnIdle {
            val v=ui.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888)
            v.draw(Canvas(b));file.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
        }
    }

    @Test fun rulesStartExpandedCollapseAndShowUpdatedSavedRules() {
        var observed by mutableStateOf(match)
        content{MatchRules(observed)}
        ui.onNodeWithText(match.rules).assertIsDisplayed()
        ui.onNodeWithTag("match-rules-toggle").assertIsOnStateDescription("Expanded")
        ui.onNodeWithTag("match-rules-toggle").performClick()
        ui.onNodeWithTag("match-rules-body").assertDoesNotExist()
        ui.runOnIdle{observed=match.copy(rules="Changed rules from match record")}
        ui.onNodeWithTag("match-rules-toggle").performClick()
        ui.onNodeWithText(observed.rules).assertIsDisplayed()
        ui.onNodeWithText(match.rules).assertDoesNotExist()
    }

    @Test fun collapsedRulesSurviveRestorationAndNewMatchDefaultsExpanded() {
        LocaleStrings.configure(ui.activity,false)
        val restoration=StateRestorationTester(ui)
        var observed by mutableStateOf(match)
        restoration.setContent{PlayerTheme{MatchRules(observed)}}
        ui.onNodeWithTag("match-rules-toggle").performClick()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithTag("match-rules-body").assertDoesNotExist()
        ui.runOnIdle{observed=match.copy(id="different-match",rules="New match rules")}
        ui.onNodeWithText("New match rules").assertIsDisplayed()
    }

    @Test fun heroAndQuietMetadataUseCurrentMatchValues() {
        var observed by mutableStateOf(match)
        content{Column{Hero(observed);MatchDetailsMetadata(observed)}}
        listOf(match.title,"৳30","৳1200","32 / 48","Starts in 00:24:18","Match ID · details-101","Status · open").forEach{ui.onNodeWithText(it).assertExists()}
        val layouts=mutableListOf<TextLayoutResult>()
        ui.onNodeWithText("Match ID · details-101").performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertTrue(layouts.single().layoutInput.style.fontSize.value<=12f)
        ui.runOnIdle{observed=match.copy(title="Revised match",fee=3050,prize=150025,joined=40,matchStatus="live")}
        listOf("Revised match","৳30.50","৳1500.25","40 / 48","Live now").forEach{ui.onNodeWithText(it).assertExists()}
        ui.onNodeWithText(match.title).assertDoesNotExist()
    }

    @Test fun detailTabsSelectOverviewPrizesAndPlayers() {
        var selected by mutableStateOf("Overview")
        content{MatchDetailsTabs(selected){selected=it}}
        listOf("Prizes","Players","Overview").forEach{tab->
            ui.onNodeWithTag("details-tab-$tab").performClick().assertIsSelected();assertEquals(tab,selected)
        }
    }

    @Test fun fixedJoinActionPassesThroughEnabledAndBusyStates() {
        var enabled by mutableStateOf(false);var busy by mutableStateOf(false);var calls=0
        content{MatchJoinBar(3050,if(busy)"Please wait…" else "Join match",enabled,busy){calls++}}
        ui.onNodeWithText("৳30.50").assertExists()
        ui.onNodeWithTag("match-join").performClick().assertIsNotEnabled();assertEquals(0,calls)
        ui.runOnIdle{enabled=true}
        ui.onNodeWithTag("match-join").performClick();assertEquals(1,calls)
        ui.runOnIdle{busy=true;enabled=false}
        ui.onNodeWithTag("match-join").performClick().assertIsNotEnabled();assertEquals(1,calls)
    }

    @Test fun minimalSoloJoinKeepsCaptainPayloadAndExactBalanceWithoutPlayerPanel() {
        var sent=listOf<String>();var busy by mutableStateOf(false);var calls=0
        content{RosterForm(match,"Captain",busy,false,"",balance=60000){sent=it;calls++;busy=true}}
        ui.onNodeWithText("Review entry").assertExists()
        ui.onNodeWithText("৳570").assertExists()
        ui.onNodeWithText("Captain").assertDoesNotExist()
        ui.onNodeWithText("Your player").assertDoesNotExist()
        ui.onNodeWithTag("join-player-2").assertDoesNotExist()
        tap("join-confirm")
        assertEquals(listOf("Captain"),sent);assertEquals(1,calls)
        ui.onNodeWithTag("join-confirm").assertIsNotEnabled().performClick();assertEquals(1,calls)
    }

    @Test fun duoRejectsDuplicateAndLongNamesThenSubmitsTrimmedRoster() {
        var sent=listOf<String>()
        content{RosterForm(match.copy(mode="Duo"),"Captain",false,false,""){sent=it}}
        ui.onNodeWithTag("join-confirm").assertIsNotEnabled()
        type(2,"captain");ui.onNodeWithTag("join-confirm").assertIsNotEnabled()
        type(2,"x".repeat(81));ui.onNodeWithTag("join-confirm").assertIsNotEnabled()
        type(2,"  Teammate  ");tap("join-confirm")
        assertEquals(listOf("Captain","Teammate"),sent)
    }

    @Test fun squadKeyboardNextAndDoneUseCurrentRosterAndBusyGuard() {
        var busy by mutableStateOf(false);var calls=0;var sent=listOf<String>()
        content{RosterForm(match.copy(mode="Squad"),"Captain",busy,false,""){sent=it;calls++;busy=true}}
        type(2,"Second");ui.onNodeWithTag("join-player-2").performImeAction()
        ui.onNodeWithTag("join-player-3").assertIsFocused()
        type(3,"Third");ui.onNodeWithTag("join-player-3").performImeAction()
        ui.onNodeWithTag("join-player-4").assertIsFocused()
        type(4,"Fourth");ui.onNodeWithTag("join-player-4").performImeAction()
        assertEquals(listOf("Captain","Second","Third","Fourth"),sent);assertEquals(1,calls)
        ui.onNodeWithTag("join-player-4").assertIsNotEnabled()
        ui.onNodeWithTag("join-confirm").performScrollTo().performClick();assertEquals(1,calls)
    }

    @Test fun serverFailureRetainsTeamNamesAndAllowsRetryWithUpdatedFee() {
        var error by mutableStateOf("");var observed by mutableStateOf(match.copy(mode="Duo"));var calls=0
        content{RosterForm(observed,"Captain",false,false,error,balance=60000){calls++;error="Connection unavailable. Retry to check the saved result."}}
        type(2,"Teammate");tap("join-confirm")
        ui.onNodeWithText(error).assertExists()
        ui.runOnIdle{observed=observed.copy(fee=4050)}
        ui.onNodeWithText("৳559.50").assertExists()
        ui.onNodeWithTag("join-player-2").assertTextContains("Teammate")
        tap("join-confirm");assertEquals(2,calls)
    }

    @Test fun rosterFieldsSurviveSavedStateRestoration() {
        LocaleStrings.configure(ui.activity,false)
        val restoration=StateRestorationTester(ui)
        restoration.setContent{PlayerTheme{RosterForm(match.copy(mode="Squad"),"Captain",false,false,"") {}}}
        type(2,"Second");type(3,"Third");type(4,"Fourth")
        restoration.emulateSavedInstanceStateRestore()
        listOf(2 to "Second",3 to "Third",4 to "Fourth").forEach{(n,name)->ui.onNodeWithTag("join-player-$n").assertTextContains(name)}
        ui.onNodeWithTag("join-confirm").assertIsEnabled()
    }

    @Test fun changedAvailabilityAndUnknownModePreventSubmission() {
        var allowed by mutableStateOf(true);var observed by mutableStateOf(match);var calls=0
        content{RosterForm(observed,"Captain",false,false,"",canJoin=allowed){calls++}}
        ui.runOnIdle{allowed=false}
        tap("join-confirm");assertEquals(0,calls)
        ui.onNodeWithTag("join-confirm").assertIsNotEnabled()
        ui.runOnIdle{allowed=true;observed=observed.copy(mode="Unknown")}
        ui.onNodeWithTag("join-confirm").assertIsNotEnabled()
    }

    @Test fun joinRequirementsKeepsSetupAndFundingActionsAndExactShortfall() {
        var missing by mutableStateOf(true);var calls=0
        content{Column(Modifier.verticalScroll(rememberScrollState())){JoinRequirements(match,missing,1050){calls++}}}
        ui.onNodeWithText("৳19.50").assertExists()
        ui.onNodeWithText("Add username").assertExists();tap("join-requirement-action")
        ui.runOnIdle{missing=false}
        ui.onNodeWithTag("join-requirement-action").assertTextContains("Add money").performClick()
        assertEquals(2,calls)
    }

    @Test fun unconfirmedRoomNeverRendersCredentialsEvenIfReadyStateIsSupplied() {
        var confirmed by mutableStateOf(false)
        content{MatchRoomContent(confirmed,"requested",RoomState.Ready("PRIVATE-ROOM","PRIVATE-PASS"),{},{},{})}
        ui.onNodeWithText("PRIVATE-ROOM").assertDoesNotExist();ui.onNodeWithText("PRIVATE-PASS").assertDoesNotExist()
        ui.onNodeWithTag("room-access-toggle").performClick()
        ui.onNodeWithText("Your entry is awaiting staff confirmation. Room details unlock after payment confirmation and room release.").assertExists()
        ui.runOnIdle{confirmed=true}
        ui.onNodeWithText("PRIVATE-ROOM").assertExists();ui.onNodeWithText("PRIVATE-PASS").assertExists()
    }

    @Test fun roomLoadingUnavailableRefreshHideAndShowRetainCallbacks() {
        var state by mutableStateOf<RoomState>(RoomState.Loading);var refreshed=0;var shown=0
        content{MatchRoomContent(true,null,state,{shown++;state=RoomState.Ready("ROOM-1","PASS-1")},{refreshed++;state=RoomState.Ready("ROOM-1","PASS-1")},{state=RoomState.Hidden})}
        ui.onNodeWithText("Checking room availability…").assertExists()
        ui.runOnIdle{state=RoomState.Unavailable("Room unavailable. Check the release time and your confirmed entry, then retry.")}
        ui.onNodeWithText("Refresh room").performClick();assertEquals(1,refreshed)
        ui.onNodeWithText("Hide").performClick();ui.onNodeWithText("ROOM-1").assertDoesNotExist()
        ui.onNodeWithText("Show room").performClick();assertEquals(1,shown)
        ui.onNodeWithText("ROOM-1").assertExists()
    }

    @Test fun roomCopyButtonsCopyTheExactCurrentIdAndPassword() {
        content{MatchRoomContent(true,null,RoomState.Ready("8402 5716","AT1024"),{},{},{})}
        val clipboard=ui.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        ui.onNodeWithText("Copy room ID").performClick()
        assertEquals("8402 5716",clipboard.primaryClip!!.getItemAt(0).text.toString())
        ui.onNodeWithText("Copy password").performClick()
        assertEquals("AT1024",clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test fun prizesSortPublishedPositiveAmountsAndKeepCacheAndRetryStates() {
        var calls=0
        content{PublicPrizeContent(listOf(PublicPrize(3,20050),PublicPrize(1,60000),PublicPrize(2,0)),true,false,true,true,0){calls++}}
        listOf("#1","#3","৳600","৳200.50","Saved prizes · reconnect to confirm","Could not load prizes.","৳0").forEach{ui.onNodeWithText(it).assertExists()}
        ui.onNodeWithText("#2").assertDoesNotExist()
        assertTrue(ui.onNodeWithText("#1").fetchSemanticsNode().positionInRoot.y<ui.onNodeWithText("#3").fetchSemanticsNode().positionInRoot.y)
        ui.onNodeWithTag("prizes-retry").performClick();assertEquals(1,calls)
    }

    @Test fun prizesDistinguishLoadingEmptyAndUnpublished() {
        var loading by mutableStateOf(true);var enabled by mutableStateOf(false)
        content{PublicPrizeContent(emptyList(),enabled,loading,false,false,null){}}
        ui.onNodeWithTag("prizes-loading").assertExists()
        ui.onNodeWithText("Prize breakdown not published.").assertDoesNotExist()
        ui.runOnIdle{loading=false}
        ui.onNodeWithText("Prize breakdown not published.").assertExists()
        ui.runOnIdle{enabled=true}
        ui.onNodeWithText("No placement prizes.").assertExists()
    }

    @Test fun playersShowOnlyPresentPublicUidsAndPreserveTeammatesPagingAndRefresh() {
        var rows by mutableStateOf(listOf(PublicParticipant("one","NightOwl",listOf("NightOwl","Teammate"),"123456789"),PublicParticipant("two","Raven",emptyList())))
        var refreshes=0;var pages=0
        content{PublicParticipantContent(rows,false,true,true,false,{refreshes++},{pages++})}
        listOf("FF UID · 123456789","Teammate","Offline").forEach{ui.onNodeWithText(it).assertExists()}
        ui.onAllNodes(hasText("FF UID ·",substring=true)).assertCountEquals(1)
        ui.onNodeWithText("FF UID · —").assertDoesNotExist()
        ui.onNodeWithTag("participants-refresh").performClick();ui.onNodeWithTag("participants-more").performClick()
        assertEquals(1,refreshes);assertEquals(1,pages)
        ui.runOnIdle{rows=listOf(rows.first().copy(name="Updated name",ffUid="987654321"))}
        ui.onNodeWithText("Updated name").assertExists();ui.onNodeWithText("FF UID · 987654321").assertExists()
        ui.onNodeWithText("Raven").assertDoesNotExist()
        ui.runOnIdle{rows=listOf(rows.first().copy(ffUid="   "))}
        ui.onAllNodes(hasText("FF UID ·",substring=true)).assertCountEquals(0)
        ui.onNodeWithText("Updated name").assertExists()
        ui.runOnIdle{rows=listOf(rows.first().copy(ffUid="112233445"))}
        ui.onNodeWithText("FF UID · 112233445").assertExists()
    }

    @Test fun playerLoadingErrorEmptyAndRetryRemainDistinct() {
        var loading by mutableStateOf(true);var error by mutableStateOf(false);var calls=0
        content{PublicParticipantContent(emptyList(),loading,false,false,error,{calls++},{})}
        ui.onNodeWithTag("participants-refresh").assertIsNotEnabled()
        ui.onNodeWithText("No participants yet.").assertDoesNotExist()
        ui.runOnIdle{loading=false;error=true}
        ui.onNodeWithContentDescription("Retry").performClick();assertEquals(1,calls)
        ui.onNodeWithText("Could not load participants. Reconnect and retry.").assertExists()
        ui.runOnIdle{error=false}
        ui.onNodeWithText("No participants yet.").assertExists()
    }

    @Test fun nativeDetailsPrizesPlayersAndJoinRenderWithApprovedHierarchy() {
        var tab by mutableStateOf("Overview");var joining by mutableStateOf(false)
        content{
            if(joining)RosterForm(match,"NightOwl",false,false,"",balance=60000){}
            else Scaffold(bottomBar={MatchJoinBar(match.fee,"Join match",true,false){joining=true}}){padding->
                LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    item{ScreenHeader("Match details",back={})};item{Hero()};item{MatchDetailsTabs(tab){tab=it}}
                    when(tab) {
                        "Overview"->{item{MatchRules(match)};item{MatchRoomContent(false,null,RoomState.Hidden,{},{},{})};item{MatchDetailsMetadata(match)}}
                        "Prizes"->item{PublicPrizeContent(listOf(PublicPrize(1,60000),PublicPrize(2,40000),PublicPrize(3,20000)),true,false,false,false,0){}}
                        "Players"->item{PublicParticipantContent(listOf(PublicParticipant("one","NightOwl",emptyList(),"123456789"),PublicParticipant("two","Raven",emptyList())),false,false,false,false,{},{})}
                    }
                }
            }
        }
        capture("details-overview-sample")
        listOf("Prizes","Players").forEach{t->ui.onNodeWithTag("details-tab-$t").performScrollTo().performClick();capture("details-${t.lowercase()}-sample")}
        ui.onNodeWithTag("match-join").performClick();capture("joining-sample")
    }

    @Test fun narrowLargeTextRulesHeroAndMetadataDoNotClip() {
        content(fontScale=2f){Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()).padding(16.dp)){Hero();MatchRules(match);MatchDetailsMetadata(match)}}
        listOf(match.title,match.rules,"Starts in 00:24:18","Match ID · ${match.id}","৳1200").forEach{assertFits(it)}
    }

    @Test @Config(qualifiers="w800dp-h360dp-xhdpi") fun banglaLargeTextJoinAndRoomActionsRemainReachableInLandscape() {
        var joining by mutableStateOf(true);var sent=false
        content(bangla=true,fontScale=2f){
            Box(Modifier.widthIn(max=640.dp).fillMaxWidth()) {
                if(joining)RosterForm(match,"Captain",false,true,"",balance=60000){sent=true;joining=false}
                else Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {MatchRoomContent(true,null,RoomState.Ready("8402-5716","AT1024"),{},{},{})}
            }
        }
        tap("join-confirm");assertTrue(sent)
        ui.onNodeWithText(LocaleStrings.get("Copy password")).performScrollTo().assertIsDisplayed()
        assertFits("AT1024")
    }
}

private fun SemanticsNodeInteraction.assertIsOnStateDescription(value:String)=assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,value))
