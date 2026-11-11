package bd.arena.prototype

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeAuthUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()

    private fun content(bangla:Boolean=false,fontScale:Float=1f,body:@Composable ()->Unit) {
        LocaleStrings.configure(ui.activity,bangla)
        ui.setContent {
            CompositionLocalProvider(LocalDensity provides Density(ui.activity.resources.displayMetrics.density,fontScale)) {
                PlayerTheme {LocalizedTypography(bangla){body()}}
            }
        }
    }
    private fun field(tag:String,value:String) {ui.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)}
    private fun tap(tag:String) {ui.onNodeWithTag(tag).performScrollTo().performClick()}
    private fun text(tag:String)=ui.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    private fun capture(name:String) {
        ui.waitForIdle()
        val file=File(System.getProperty("java.io.tmpdir"),"antihack-home-auth-ui/$name.png")
        file.parentFile!!.mkdirs()
        ui.runOnIdle {
            val view=ui.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }

    @Test fun signInPassesEnteredCredentialsOnceAndDisablesPendingForm() {
        var busy by mutableStateOf(false)
        var calls=0
        var sent=listOf<String>()
        var finish:(()->Unit)?=null
        content {AuthContent(false,busy,"",{},{},{}) {creating,email,password,confirmation,done->
            assertFalse(creating);calls++;sent=listOf(email,password,confirmation);busy=true;finish=done
        }}
        field("auth-email","player@arena.test")
        field("auth-password","PlayerPass123!")
        tap("auth-submit")
        assertEquals(listOf("player@arena.test","PlayerPass123!",""),sent)
        ui.onNodeWithTag("auth-submit").assertIsNotEnabled()
        ui.onNodeWithTag("auth-email").assertIsNotEnabled()
        ui.onNodeWithTag("auth-password").assertIsNotEnabled()
        tap("auth-submit")
        assertEquals(1,calls)
        ui.runOnIdle {finish!!();busy=false}
        assertEquals("",text("auth-password"))
        assertEquals("player@arena.test",text("auth-email"))
    }

    @Test fun switchingFormsKeepsEmailClearsPasswordsAndBackReturnsToSignIn() {
        var closes=0
        var clears=0
        content {AuthContent(false,false,"",{},{closes++},{clears++}) {_,_,_,_,_->}}
        field("auth-email","returning@arena.test")
        field("auth-password","OldPassword123!")
        tap("auth-toggle")
        ui.onNodeWithTag("auth-confirmation").assertExists()
        assertEquals("returning@arena.test",text("auth-email"))
        assertEquals("",text("auth-password"))
        field("auth-password","NewPassword123!")
        field("auth-confirmation","NewPassword123!")
        tap("auth-back")
        ui.onNodeWithTag("auth-confirmation").assertDoesNotExist()
        assertEquals("",text("auth-password"))
        assertEquals(0,closes)
        tap("auth-continue")
        assertEquals(1,closes)
        assertEquals(2,clears)
    }

    @Test fun registrationUsesExistingValidationAndKeepsValuesOnFailure() {
        var error by mutableStateOf("")
        var registered=false
        content {AuthContent(false,false,error,{},{},{error=""}) {creating,email,password,confirmation,done->
            assertTrue(creating)
            error=registrationError(email,password,confirmation).orEmpty()
            if(error.isEmpty()){registered=true;done()}
        }}
        tap("auth-toggle")
        field("auth-email","signup@arena.test")
        field("auth-password","PlayerPass123!")
        field("auth-confirmation","Different123!")
        tap("auth-submit")
        ui.onNodeWithText("Passwords do not match.").assertExists()
        ui.onNodeWithTag("auth-confirmation").assertIsFocused()
        assertFalse(registered)
        assertEquals("signup@arena.test",text("auth-email"))
        field("auth-confirmation","PlayerPass123!")
        ui.onNodeWithTag("auth-confirmation").performImeAction()
        ui.runOnIdle{assertTrue(registered)}
        assertEquals("",text("auth-password"))
        assertEquals("",text("auth-confirmation"))
    }

    @Test fun keyboardNextAndDoneUseTheSameSignInActionAsTheButton() {
        var calls=0
        content {AuthContent(false,false,"",{},{},{}) {creating,email,password,_,_->
            assertFalse(creating);assertEquals("keyboard@arena.test",email);assertEquals("Keyboard123!",password);calls++
        }}
        field("auth-email","keyboard@arena.test")
        ui.onNodeWithTag("auth-email").performImeAction()
        ui.onNodeWithTag("auth-password").assertIsFocused()
        field("auth-password","Keyboard123!")
        ui.onNodeWithTag("auth-password").performImeAction()
        ui.runOnIdle{assertEquals(1,calls)}
    }

    @Test fun serverFailureKeepsCredentialsAvailableForRetry() {
        var error by mutableStateOf("")
        var attempts=0
        content {AuthContent(false,false,error,{},{},{error=""}) {creating,email,password,_,_->
            assertFalse(creating);assertEquals("retry@arena.test",email);assertEquals("RetryPass123!",password)
            attempts++;error="Sign in failed. Check email, password and connection."
        }}
        field("auth-email","retry@arena.test")
        field("auth-password","RetryPass123!")
        tap("auth-submit")
        ui.onNodeWithText("Sign in failed. Check email, password and connection.").assertExists()
        assertEquals("retry@arena.test",text("auth-email"))
        tap("auth-submit")
        assertEquals(2,attempts)
    }

    @Test fun savedStateRetainsEmailAndRegistrationModeWithoutSavingPasswords() {
        LocaleStrings.configure(ui.activity,false)
        val restoration=StateRestorationTester(ui)
        restoration.setContent {PlayerTheme {AuthContent(false,false,"",{},{},{}) {_,_,_,_,_->}}}
        field("auth-email","restored@arena.test")
        tap("auth-toggle")
        field("auth-password","NeverSave123!")
        field("auth-confirmation","NeverSave123!")
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithTag("auth-confirmation").assertExists()
        assertEquals("restored@arena.test",text("auth-email"))
        assertEquals("",text("auth-password"))
        assertEquals("",text("auth-confirmation"))
    }

    @Test fun staleSignInActionCannotSubmitAfterSwitchingToRegistration() {
        var error by mutableStateOf("")
        var registration:Boolean?=null
        content {AuthContent(false,false,error,{},{},{error=""}) {creating,_,_,_,_->registration=creating;error="Enter a valid email address."}}
        val outgoing=ui.onNodeWithTag("auth-submit").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        tap("auth-toggle")
        ui.onNodeWithTag("auth-confirmation").assertExists()
        ui.runOnIdle{outgoing()}
        assertNull(registration)
        tap("auth-submit")
        assertEquals(true,registration)
        ui.onNodeWithTag("auth-email").assertIsFocused()
    }

    @Test
    @Config(qualifiers="w640dp-h360dp-land-xhdpi")
    fun banglaDoubleTextLandscapeCanReachRegistrationAndGuestExit() {
        var closes=0
        content(bangla=true,fontScale=2f) {AuthContent(true,false,"",{},{closes++},{}) {_,_,_,_,_->}}
        tap("auth-toggle")
        ui.onNodeWithText("পাসওয়ার্ড নিশ্চিত করুন").assertExists()
        ui.onNodeWithTag("auth-submit").performScrollTo().assertIsDisplayed()
        tap("auth-continue")
        assertEquals(1,closes)
    }

    @Test fun passwordVisibilityAppliesToBothRegistrationFieldsAndResetsOnModeChange() {
        content {AuthContent(false,false,"",{},{},{}) {_,_,_,_,_->}}
        tap("auth-toggle")
        field("auth-password","Visible123!")
        field("auth-confirmation","Visible123!")
        assertTrue(ui.onNodeWithTag("auth-password").fetchSemanticsNode().config.contains(SemanticsProperties.Password))
        assertTrue(ui.onNodeWithTag("auth-confirmation").fetchSemanticsNode().config.contains(SemanticsProperties.Password))
        ui.onNodeWithText("Show").performScrollTo().performClick()
        assertFalse(ui.onNodeWithTag("auth-password").fetchSemanticsNode().config.contains(SemanticsProperties.Password))
        assertFalse(ui.onNodeWithTag("auth-confirmation").fetchSemanticsNode().config.contains(SemanticsProperties.Password))
        tap("auth-back")
        assertTrue(ui.onNodeWithTag("auth-password").fetchSemanticsNode().config.contains(SemanticsProperties.Password))
    }

    @Test fun updatedCategoryAndAnnouncementPayloadsRemainClickableWithoutHardcodedNames() {
        var category by mutableStateOf(MatchCategory("server-category","Database category",0,true))
        val original=Notice("server-notice","Database announcement","Body supplied by Firebase",100L,1L)
        var feed by mutableStateOf(NoticeFeed(listOf(original),loading=false))
        var opened:Notice?=null
        var chosen=""
        var explored=false
        content {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            HomeAnnouncement(feed,{opened=it},{})
            CategoryTile(category,false,Modifier.width(150.dp),{chosen=category.id}) {imageModifier->
                Image(painterResource(R.drawable.mode_solo),null,imageModifier,contentScale=ContentScale.Crop)
            }
            HomeExploreShortcut{explored=true}
        }}
        ui.onNodeWithTag("category-server-category").performClick()
        assertEquals("server-category",chosen)
        ui.onNodeWithTag("home-announcement").performClick()
        assertEquals(original,opened)
        ui.runOnIdle {
            category=category.copy(id="new-server-id",name="Renamed by admin")
            feed=NoticeFeed(listOf(original.copy(title="Admin revision",body="Fresh server body",revision=2L)),loading=false)
        }
        ui.onNodeWithText("Database category").assertDoesNotExist()
        ui.onNodeWithText("Renamed by admin").assertExists()
        ui.onNodeWithText("Admin revision").assertExists()
        ui.onNodeWithTag("category-new-server-id").performClick()
        assertEquals("new-server-id",chosen)
        ui.onNodeWithTag("home-announcement").performClick()
        assertEquals(2L,opened!!.revision)
        ui.onNodeWithTag("home-explore-shortcut").performClick()
        assertTrue(explored)
    }

    @Test fun cachedAnnouncementsRetainReadableContentAndRetry() {
        val notice=Notice("cached","Saved admin update","Saved body",100L,1L)
        var retries=0
        content {Column(Modifier.padding(16.dp)) {HomeAnnouncement(NoticeFeed(listOf(notice),loading=false,cached=true,error=true),{},{retries++})}}
        ui.onNodeWithText("Saved admin update").assertExists()
        ui.onNodeWithText("Could not refresh announcements.").assertExists()
        ui.onNodeWithText("Retry").performClick()
        assertEquals(1,retries)
    }

    @Test fun longDatabaseCategoryNameWrapsAtDoubleTextSize() {
        val name="Long category name supplied by the tournament admin"
        content(fontScale=2f) {CategoryTile(MatchCategory("long-name",name,0,true),false,Modifier.width(150.dp),{}) {m->
            Image(painterResource(R.drawable.mode_solo),null,m,contentScale=ContentScale.Crop)
        }}
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        ui.onNodeWithText(name,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertEquals(1,layouts.size)
        assertFalse(layouts.single().hasVisualOverflow)
        val textBounds=ui.onNodeWithText(name,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val cardBounds=ui.onNodeWithTag("category-long-name").fetchSemanticsNode().boundsInRoot
        assertTrue(textBounds.bottom<=cardBounds.bottom)
        assertTrue(textBounds.right<=cardBounds.right)
    }

    @Test fun largeTextHeaderKeepsLogoBadgeAndNotificationActionInBounds() {
        var opened=false
        content(fontScale=2f) {Box(Modifier.padding(16.dp)){ScreenHeader("Antihack Tour",actions={NotificationAction(128){opened=true}})}}
        val header=ui.onNodeWithTag("screen-header").fetchSemanticsNode().boundsInRoot
        val logo=ui.onNodeWithTag("home-logo").fetchSemanticsNode().boundsInRoot
        val action=ui.onNodeWithTag("notification-action").fetchSemanticsNode().boundsInRoot
        val badge=ui.onNodeWithTag("notification-badge",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertEquals(header.left,logo.left,1f)
        assertTrue(logo.right<=action.left)
        assertTrue(action.right<=header.right)
        assertTrue(badge.left>=action.left&&badge.right<=action.right)
        ui.onNodeWithContentDescription("Notifications").performClick()
        assertTrue(opened)
        ui.onNodeWithText("99+",useUnmergedTree=true).assertExists()
    }

    @Test fun reducedMotionLoadingHasStableAccessiblePlaceholders() {
        val old=ReflectionHelpers.getStaticField<Float>(ValueAnimator::class.java,"sDurationScale")
        try {
            ReflectionHelpers.setStaticField(ValueAnimator::class.java,"sDurationScale",0f)
            assertFalse(ValueAnimator.areAnimatorsEnabled())
            content {HomeStartup()}
            ui.onNodeWithTag("home-categories-loading").assertExists()
            ui.onNodeWithContentDescription("Loading categories…").assertExists()
        }finally {ReflectionHelpers.setStaticField(ValueAnimator::class.java,"sDurationScale",old)}
    }

    @Test fun nativeFormRendersWithOriginalArtworkAndSimpleTitles() {
        content {AuthContent(false,false,"",{},{},{}) {_,_,_,_,_->}}
        capture("sign-in")
        tap("auth-toggle")
        capture("create-account")
        ui.onNodeWithText("Make your mark.").assertDoesNotExist()
        ui.onNodeWithContentDescription("Antihack Tour").assertExists()
    }

    @Test fun nativeHomeRendersCategoriesWithoutEsportsOrCategoryTaglines() {
        val notice=Notice("fixture","Sample · Firebase announcement","Sample notice body for native rendering.",100L,1L)
        content {LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            item{ScreenHeader("Antihack Tour",actions={NotificationAction(2){}})}
            item{HomeAnnouncement(NoticeFeed(listOf(notice),loading=false),{},{})}
            item{HomeCategoryHeading()}
            item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                CategoryTile(MatchCategory("fixture-solo","Battle Royale",0,true),false,Modifier.weight(1f),{}) {m->Image(painterResource(R.drawable.mode_solo),null,m,contentScale=ContentScale.Crop)}
                CategoryTile(MatchCategory("fixture-squad","Clash Squad",1,true),false,Modifier.weight(1f),{}) {m->Image(painterResource(R.drawable.mode_squad),null,m,contentScale=ContentScale.Crop)}
            }}
            item{HomeExploreShortcut{}}
        }}
        ui.onNodeWithText("Battle Royale").assertExists()
        ui.onNodeWithText("ESPORTS").assertDoesNotExist()
        ui.onNodeWithText("OUTLAST THE LOBBY").assertDoesNotExist()
        capture("home-components-sample")
    }
}
