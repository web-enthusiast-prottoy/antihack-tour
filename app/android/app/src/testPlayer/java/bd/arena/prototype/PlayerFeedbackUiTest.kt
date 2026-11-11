package bd.arena.prototype

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerFeedbackUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val match=Tournament("one","Match","Solo","Today",1,48,description="Saved description from the match record.",rules="Saved match rules.")
    private fun content(large:Boolean=false,bangla:Boolean=false,body:@Composable ()->Unit) {
        LocaleStrings.configure(ui.activity,bangla)
        ui.setContent {CompositionLocalProvider(LocalDensity provides Density(ui.activity.resources.displayMetrics.density,if(large)2f else 1f)) {
            PlayerTheme{LocalizedTypography(bangla){body()}}
        }}
    }
    @Test fun upcomingDoesNotExposePublicFeedPagingButExploreRetainsIt() {
        var route by mutableStateOf("Matches");var loading by mutableStateOf(false);var calls=0
        content{MatchFeedPaging(route,true,loading){calls++}}
        ui.onNodeWithTag("matches-load-more").assertDoesNotExist()
        ui.runOnIdle{route="Explore"}
        ui.onNodeWithTag("matches-load-more").performClick();assertEquals(1,calls)
        ui.runOnIdle{loading=true}
        ui.onNodeWithTag("matches-load-more").assertIsNotEnabled()
    }
    @Test fun ownMatchesBeyondFirstPageLoadAutomaticallyAndStopAtKnownOrExhaustedRecords() {
        assertTrue(needsUpcomingPage(setOf("one"),listOf(match.copy(id="other")),true,false,false))
        assertFalse(needsUpcomingPage(setOf("one"),listOf(match),true,false,false))
        assertFalse(needsUpcomingPage(emptySet(),emptyList(),true,false,false))
        assertFalse(needsUpcomingPage(setOf("one"),emptyList(),false,false,false))
        assertFalse(needsUpcomingPage(setOf("one"),emptyList(),true,true,false))
        assertFalse(needsUpcomingPage(setOf("one"),emptyList(),true,false,true))
    }
    @Test fun resultsSearchPrecedesScopeFiltersAndKeepsBothCallbacks() {
        var input by mutableStateOf("");var own by mutableStateOf(true);var submits=0
        content{Column(Modifier.padding(16.dp)){ResultsSearchFilters(input,{input=it},own,true,{own=it},{submits++})}}
        val search=ui.onNodeWithTag("results-search").fetchSemanticsNode().boundsInRoot
        val filters=ui.onNodeWithTag("results-scope-own").fetchSemanticsNode().boundsInRoot
        assertTrue(search.bottom<=filters.top)
        ui.onNodeWithTag("results-search").performTextInput("one");ui.onNodeWithTag("results-search").performImeAction()
        assertEquals("one",input);assertEquals(1,submits)
        ui.onNodeWithTag("results-scope-all").performClick().assertIsSelected();assertFalse(own)
    }
    @Test fun guestResultsRetainsSearchWithoutPrivateScopeFilter() {
        content{ResultsSearchFilters("",{},false,false,{},{})}
        ui.onNodeWithTag("results-search").assertExists();ui.onNodeWithTag("results-scope-own").assertDoesNotExist()
    }
    @Test fun walletFilterAlignsAtRightAndRefreshStaysAtBottomAfterScrolling() {
        var refreshes=0
        content{FixedRefreshHost {
            RegisterRefresh{refreshes++}
            LazyColumn(Modifier.fillMaxSize().testTag("wallet-test-list"),contentPadding=PaddingValues(16.dp)) {
                item{WalletActivityHeader("All activity",{})}
                items(20){Text("Activity row $it",Modifier.height(80.dp))}
            }
        }}
        val header=ui.onNodeWithTag("wallet-activity-header").fetchSemanticsNode().boundsInRoot
        val filter=ui.onNodeWithTag("activity-filter").fetchSemanticsNode().boundsInRoot
        assertEquals(header.right,filter.right,1f)
        ui.onNodeWithTag("activity-refresh").assertDoesNotExist();ui.onNodeWithText("Refresh").assertDoesNotExist()
        ui.onNodeWithTag("wallet-test-list").performScrollToIndex(20)
        ui.onNodeWithText("Activity row 19").assertIsDisplayed()
        ui.onNodeWithTag("fixed-refresh").assertIsDisplayed().performClick();assertEquals(1,refreshes)
        ui.onNodeWithTag("fixed-refresh").assertHeightIsEqualTo(52.dp)
    }
    @Test fun fixedRefreshUsesCurrentActionAndDisablesWhileBusy() {
        var busy by mutableStateOf(false);var count=0
        content{FixedRefreshHost{RegisterRefresh(!busy){count++};Text("Match details")}}
        ui.onNodeWithTag("fixed-refresh").performClick();assertEquals(1,count)
        ui.runOnIdle{busy=true}
        ui.onNodeWithTag("fixed-refresh").assertIsNotEnabled().performClick();assertEquals(1,count)
        ui.runOnIdle{busy=false};ui.onNodeWithTag("fixed-refresh").performClick();assertEquals(2,count)
    }
    @Test fun walletActivityRefreshReloadsQueriesAndKeepsSelectedFilter() {
        val session=mock(AdminSession::class.java)
        val db=mock(FirebaseFirestore::class.java)
        val query=mock(CollectionReference::class.java,RETURNS_SELF)
        val snapshot=mock(QuerySnapshot::class.java)
        `when`(session.uid).thenReturn("player");`when`(session.db).thenReturn(db)
        `when`(db.collection(anyString())).thenReturn(query)
        `when`(snapshot.documents).thenReturn(emptyList())
        `when`(snapshot.metadata).thenReturn(mock(SnapshotMetadata::class.java))
        `when`(query.get()).thenReturn(Tasks.forResult(snapshot))
        var refresh by mutableIntStateOf(0)
        content{WalletHistory(session,emptyList(),refresh)}
        ui.waitForIdle()
        val paths=if(BuildConfig.LOCAL_OPERATIONS)3 else 2
        verify(query,times(paths)).get()
        ui.onNodeWithTag("activity-filter").performClick()
        ui.onNodeWithText("Deposit").performClick()
        ui.runOnIdle{refresh++}
        ui.waitForIdle()
        verify(query,times(paths*2)).get()
        ui.onNodeWithTag("activity-filter").assertTextEquals("Deposit")
    }
    @Test fun descriptionLeadsOverviewAndDoesNotAddParticipantsOrRepeatDescription() {
        content{Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {MatchOverviewPanel(match){MatchRoomContent(false,null,RoomState.Hidden,{},{},{})}}}
        val description=ui.onNodeWithText("Description").fetchSemanticsNode().positionInRoot.y
        val rules=ui.onNodeWithText("Match rules").fetchSemanticsNode().positionInRoot.y
        val room=ui.onNodeWithText("Room access").fetchSemanticsNode().positionInRoot.y
        val meta=ui.onNodeWithTag("details-metadata").fetchSemanticsNode().positionInRoot.y
        assertTrue(description<rules&&rules<room&&room<meta)
        ui.onAllNodesWithText(match.description).assertCountEquals(1)
        ui.onNodeWithText("Participants").assertDoesNotExist();ui.onNodeWithText("Your registered roster").assertDoesNotExist()
    }
    @Test fun descriptionAndRulesExpandIndependentlyAndEmptyDescriptionIsOmitted() {
        var value by mutableStateOf(match)
        content{Column{MatchDescription(value);MatchRules(value)}}
        ui.onNodeWithTag("match-description-toggle").performClick();ui.onNodeWithTag("match-description-body").assertDoesNotExist()
        ui.onNodeWithTag("match-rules-body").assertExists()
        ui.onNodeWithTag("match-description-toggle").performClick();ui.onNodeWithTag("match-rules-toggle").performClick()
        ui.onNodeWithTag("match-description-body").assertExists();ui.onNodeWithTag("match-rules-body").assertDoesNotExist()
        ui.runOnIdle{value=value.copy(description=" ")};ui.onNodeWithTag("match-description-toggle").assertDoesNotExist()
    }
    @Test fun deniedMissingAndDisabledRoomsWaitWithoutExposingCredentials() {
        assertEquals(RoomState.Waiting,roomResponseState(403,"private response"))
        assertEquals(RoomState.Waiting,roomResponseState(404,""))
        assertEquals(RoomState.Waiting,roomResponseState(200,"""{"fields":{"enabled":{"booleanValue":false},"roomId":{"stringValue":"SECRET"},"password":{"stringValue":"PASS"}}}"""))
        assertEquals(RoomState.Waiting,roomResponseState(200,"""{"fields":{"enabled":{"booleanValue":true}}}"""))
    }
    @Test fun releasedRoomsParseExactCredentialsAndRealFailuresRemainRetryable() {
        assertEquals(RoomState.Ready("8402 5716","AT1024"),roomResponseState(200,"""{"fields":{"enabled":{"booleanValue":true},"roomId":{"stringValue":"8402 5716"},"password":{"stringValue":"AT1024"}}}"""))
        assertTrue(roomResponseState(401,"") is RoomState.Unavailable)
        assertTrue(roomResponseState(503,"") is RoomState.Unavailable)
        assertTrue(roomResponseState(0,"") is RoomState.Unavailable)
        assertTrue(roomResponseState(200,"bad response") is RoomState.Unavailable)
    }
    @Test fun unpublishedRoomMessageIsNotAnUnavailableErrorAndCanRefresh() {
        var calls=0
        content{MatchRoomContent(true,null,RoomState.Waiting,{},{calls++},{})}
        ui.onNodeWithText("Room details have not been published or released for your entry yet.").assertExists()
        ui.onAllNodes(hasText("unavailable",substring=true,ignoreCase=true)).assertCountEquals(0)
        ui.onNodeWithText("Room ID").assertDoesNotExist()
        ui.onNodeWithText("Refresh room").performClick();assertEquals(1,calls)
    }
    @Test fun largeBanglaSearchFilterAndBottomRefreshRemainReachable() {
        content(large=true,bangla=true){FixedRefreshHost {
            RegisterRefresh{}
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                ResultsSearchFilters("",{},true,true,{},{})
                WalletActivityHeader("All activity",{})
            }
        }}
        ui.onNodeWithTag("results-scope-all").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("activity-filter").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("fixed-refresh").assertIsDisplayed()
    }
}
