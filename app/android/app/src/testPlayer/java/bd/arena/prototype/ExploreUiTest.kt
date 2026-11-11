package bd.arena.prototype

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
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
import org.robolectric.util.ReflectionHelpers
import java.io.File

/** Native Compose host tests. Fixtures exercise rendering; they are not a live Firebase connection. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val now=java.time.Instant.parse("2026-11-10T18:00:00Z").toEpochMilli()
    private val match=Tournament("explore-101","Evening Solo","Solo","11 Nov · 12:24 AM",32,48,fee=3050,prize=120000,
        startsAtMillis=now+1_458_000,map="Bermuda",categoryId="battle",artworkId="saved-artwork")

    private fun content(bangla:Boolean=false,fontScale:Float=1f,body:@Composable ()->Unit) {
        LocaleStrings.configure(ui.activity,bangla)
        ui.setContent {
            CompositionLocalProvider(LocalDensity provides Density(ui.activity.resources.displayMetrics.density,fontScale)) {
                PlayerTheme {LocalizedTypography(bangla){Box(Modifier.fillMaxSize()){body()}}}
            }
        }
    }
    @Composable private fun SampleCard(value:Tournament=match,entry:String?=null,time:Long=now,open:()->Unit={}) {
        ExploreMatchCard(value,entry,time,open) { modifier ->
            Image(painterResource(modeArtwork(value.image)),null,modifier,contentScale=ContentScale.Crop)
        }
    }
    private fun assertFits(text:String) {
        val layouts=mutableListOf<TextLayoutResult>()
        ui.onNodeWithText(text,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertTrue("Missing text layout: $text",layouts.isNotEmpty())
        // Compose can retain a wider cached paragraph than the Text's final wrap-content width.
        // Check painted line edges and truncation instead of treating that empty space as clipping.
        layouts.forEach { layout ->
            assertFalse("Height clipped: $text",layout.didOverflowHeight)
            repeat(layout.lineCount) { line ->
                assertFalse("Truncated: $text",layout.isLineEllipsized(line))
                assertTrue("Left clipped: $text",layout.getLineLeft(line)>=-1f)
                assertTrue("Right clipped: $text",layout.getLineRight(line)<=layout.size.width+1f)
                assertTrue("Bottom clipped: $text",layout.getLineBottom(line)<=layout.size.height+1f)
            }
        }
    }
    private fun assertBadgeInsideImage(id:String=match.id) {
        val badge=ui.onNodeWithTag("explore-start-$id",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val image=ui.onNodeWithTag("match-image-$id",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(badge.left>=image.left&&badge.top>=image.top&&badge.right<=image.right&&badge.bottom<=image.bottom)
    }
    private fun capture(name:String) {
        ui.waitForIdle()
        val file=File(System.getProperty("java.io.tmpdir"),"antihack-explore-ui/$name.png")
        file.parentFile!!.mkdirs()
        ui.runOnIdle {
            val view=ui.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }

    @Test fun compactModePillsSelectEveryExistingModeAndRespondToExternalFilterChanges() {
        var mode by mutableStateOf("All")
        content {ExploreControls("",{},mode,{mode=it},0,{})}
        listOf("All","Solo","Duo","Squad").forEach { option ->
            ui.onNodeWithTag("explore-mode-$option").performClick().assertIsSelected()
            assertEquals(option,mode)
            val bounds=ui.onNodeWithTag("explore-mode-$option").fetchSemanticsNode().touchBoundsInRoot
            with(ui.density){assertTrue(bounds.height>=48.dp.toPx());assertTrue(bounds.width>=48.dp.toPx())}
        }
        ui.runOnIdle{mode="Solo"}
        ui.onNodeWithTag("explore-mode-Solo").assertIsSelected()
        ui.onNodeWithTag("explore-mode-Squad").assertIsNotSelected()
    }

    @Test fun searchClearKeyboardAndFilterActionRetainTheirCallbacks() {
        var search by mutableStateOf("")
        var opened=0
        content {ExploreControls(search,{search=it},"All",{},3,{opened++})}
        ui.onNodeWithTag("explore-search").performTextInput(" explore-101 ")
        ui.onNodeWithTag("explore-search").performImeAction()
        assertEquals(" explore-101 ",search)
        ui.onNodeWithContentDescription("Clear search").performClick()
        assertEquals("",search)
        ui.onNodeWithContentDescription("Filters: 3").performClick()
        assertEquals(1,opened)
    }

    @Test fun cardShowsStoredFieldsExactPaisaAndWholeCardNavigation() {
        var opened=""
        content {SampleCard(open={opened=match.id})}
        listOf(match.title,"Solo · Bermuda · ${match.time}","৳30.50","৳1200","32 / 48 joined","Starts in 00:24:18").forEach {
            ui.onNodeWithText(it,useUnmergedTree=true).assertExists()
        }
        ui.onNodeWithTag("match-card-${match.id}").performClick()
        assertEquals(match.id,opened)
        ui.onAllNodes(hasClickAction()).assertCountEquals(1)
        ui.onNodeWithTag("match-image-${match.id}",useUnmergedTree=true).assertHeightIsEqualTo(108.dp)
        assertBadgeInsideImage()
    }

    @Test fun replacingObservedMatchValuesUpdatesCardWithoutStaleTitlePriceOrCount() {
        var observed by mutableStateOf(match)
        var opened=""
        content {SampleCard(observed,open={opened=observed.id})}
        ui.runOnIdle{observed=match.copy(title="Updated from match record",joined=48,fee=4000,prize=200025,time="12 Nov · 8:30 PM",map="Kalahari")}
        listOf("Updated from match record","Solo · Kalahari · 12 Nov · 8:30 PM","৳40","৳2000.25","48 / 48 joined","Match full").forEach {
            ui.onNodeWithText(it,useUnmergedTree=true).assertExists()
        }
        ui.onNodeWithText(match.title,useUnmergedTree=true).assertDoesNotExist()
        ui.onNodeWithText("৳30.50",useUnmergedTree=true).assertDoesNotExist()
        ui.onNodeWithTag("match-card-${match.id}").performClick()
        assertEquals(match.id,opened)
    }

    @Test fun entryClosedFullAndCancelledStatesRemainVisible() {
        var observed by mutableStateOf(match)
        var entry by mutableStateOf<String?>("confirmed")
        content {SampleCard(observed,entry)}
        ui.onNodeWithText("View room details",useUnmergedTree=true).assertExists()
        ui.runOnIdle{entry="pending"}
        ui.onNodeWithText(LocaleStrings.get("pending"),useUnmergedTree=true).assertExists()
        ui.runOnIdle{entry=null;observed=match.copy(closed=true,matchStatus="closed")}
        ui.onNodeWithText("Closed",useUnmergedTree=true).assertExists()
        ui.runOnIdle{observed=match.copy(joined=48)}
        ui.onNodeWithText("Match full",useUnmergedTree=true).assertExists()
        ui.runOnIdle{entry="confirmed";observed=match.copy(matchStatus="cancelled",closed=true)}
        ui.onNodeWithText("View room details",useUnmergedTree=true).assertDoesNotExist()
        ui.onAllNodesWithText("Cancelled",useUnmergedTree=true).assertCountEquals(2)
    }

    @Test fun countdownUsesCeilingSecondsDaysAndStatusWithoutNegativeTime() {
        LocaleStrings.configure(ui.activity,false)
        assertEquals("Starts in 00:00:01",exploreStartText(match.copy(startsAtMillis=now+1),now))
        assertEquals("Starts in 1d · 00:00:00",exploreStartText(match.copy(startsAtMillis=now+86_400_000),now))
        assertEquals("Starts in 2d · 03:04:05",exploreStartText(match.copy(startsAtMillis=now+183_845_000),now))
        assertEquals("Scheduled start reached",exploreStartText(match,match.startsAtMillis))
        assertEquals("Scheduled start reached",exploreStartText(match,match.startsAtMillis+1))
        assertEquals("Scheduled",exploreStartText(match.copy(startsAtMillis=0),now))
        mapOf("live" to "Live now","review" to "Results pending","settled" to "Completed","cancelled" to "Cancelled").forEach { (status,label) ->
            assertEquals(label,exploreStartText(match.copy(matchStatus=status),now))
        }
    }

    @Test fun countdownRecomposesAtStartAndWithServerStatusChanges() {
        var clock by mutableStateOf(match.startsAtMillis-1)
        var observed by mutableStateOf(match)
        content {SampleCard(observed,time=clock)}
        ui.onNodeWithText("Starts in 00:00:01",useUnmergedTree=true).assertExists()
        ui.runOnIdle{clock=match.startsAtMillis}
        ui.onNodeWithText("Scheduled start reached",useUnmergedTree=true).assertExists()
        ui.runOnIdle{observed=match.copy(matchStatus="live")}
        ui.onNodeWithText("Live now",useUnmergedTree=true).assertExists()
    }

    @Test fun zeroCapacityAndOverfilledRecordsStillShowExactCountsWithoutInvalidProgress() {
        var observed by mutableStateOf(match.copy(joined=0,capacity=0))
        content {SampleCard(observed)}
        ui.onNodeWithText("0 / 0 joined",useUnmergedTree=true).assertExists()
        ui.runOnIdle{observed=match.copy(joined=60,capacity=48)}
        ui.onNodeWithText("60 / 48 joined",useUnmergedTree=true).assertExists()
        ui.onNodeWithText("Match full",useUnmergedTree=true).assertExists()
    }

    @Test fun compactEnglishFeedRendersApprovedHierarchy() {
        content {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                ScreenHeader("Explore")
                ExploreControls("",{},"All",{},0,{})
                SampleCard()
                SampleCard(match.copy(id="explore-102",title="Squad Showdown",mode="Squad",image="Squad",fee=6000,prize=240000,joined=24))
            }
        }
        assertFits(match.title)
        assertBadgeInsideImage()
        capture("explore-sample")
    }

    @Test fun narrowEnglishLargeTextWrapsPillsCountdownAndCardWithoutClipping() {
        val long=match.copy(title="Championship qualifiers · Evening Solo",startsAtMillis=now+183_845_000)
        content(fontScale=2f) {
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                ExploreControls("",{},"All",{},5,{})
                SampleCard(long,"confirmed")
            }
        }
        ui.onNodeWithTag("explore-mode-Squad").performScrollTo().assertIsDisplayed()
        listOf(long.title,"Starts in 2d · 03:04:05","৳30.50","৳1200","32 / 48 joined","View room details").forEach{assertFits(it)}
        assertBadgeInsideImage()
        capture("explore-large-text")
    }

    @Test fun narrowBanglaLargeTextKeepsLabelsAndCountdownReadable() {
        val long=match.copy(title="সন্ধ্যার সোলো চ্যাম্পিয়নশিপ",startsAtMillis=now+183_845_000)
        content(bangla=true,fontScale=2f) {
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                ExploreControls("",{},"Solo",{},2,{})
                SampleCard(long,"confirmed")
            }
        }
        ui.onNodeWithTag("explore-mode-Squad").performScrollTo().assertIsDisplayed()
        listOf(long.title,exploreStartText(long,now),LocaleStrings.get("Entry fee"),LocaleStrings.get("Prize pool"),"32 / 48 "+LocaleStrings.get("joined"),LocaleStrings.get("View room details")).forEach{assertFits(it)}
        assertBadgeInsideImage()
        val fee=ui.onNodeWithText("৳30.50",useUnmergedTree=true).fetchSemanticsNode().positionInRoot.y
        val prize=ui.onNodeWithText("৳1200",useUnmergedTree=true).fetchSemanticsNode().positionInRoot.y
        assertEquals("Amounts stay aligned when the Bangla prize label wraps",fee,prize,1f)
        capture("explore-bangla-large-text")
    }

    @Test fun initialLoadingUsesNonInteractivePlaceholderWithAccessibleLabel() {
        content {ExploreLoadingCard()}
        ui.onNodeWithTag("explore-loading").assertContentDescriptionEquals("Loading matches…")
        ui.onAllNodes(hasClickAction()).assertCountEquals(0)
        ui.onNodeWithText(match.title).assertDoesNotExist()
    }

    @Test fun reducedMotionLoadingRetainsSearchAndModeActions() {
        val old=ReflectionHelpers.getStaticField<Float>(ValueAnimator::class.java,"sDurationScale")
        try {
            ReflectionHelpers.setStaticField(ValueAnimator::class.java,"sDurationScale",0f)
            assertFalse(ValueAnimator.areAnimatorsEnabled())
            var mode by mutableStateOf("All")
            content {Column {ExploreControls("",{},mode,{mode=it},0,{});ExploreLoadingCard()}}
            ui.onNodeWithTag("explore-loading").assertExists()
            ui.onNodeWithTag("explore-mode-Duo").performClick().assertIsSelected()
        }finally {ReflectionHelpers.setStaticField(ValueAnimator::class.java,"sDurationScale",old)}
    }

    @Test @Config(qualifiers="w800dp-h360dp-xhdpi") fun landscapeLargeTextCanReachWholeCardAndOpenIt() {
        var opened=false
        content(bangla=true,fontScale=2f) {
            Column(Modifier.widthIn(max=640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                ExploreControls("",{},"All",{},0,{})
                SampleCard(entry="confirmed",open={opened=true})
            }
        }
        ui.onNodeWithText(LocaleStrings.get("View room details"),useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        assertFits(LocaleStrings.get("View room details"))
        ui.onNodeWithTag("match-card-${match.id}").performClick()
        assertTrue(opened)
    }
}
