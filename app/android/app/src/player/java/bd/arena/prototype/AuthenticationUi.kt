package bd.arena.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun AuthScreen(session:AdminSession,bangla:Boolean,language:(Boolean)->Unit,close:()->Unit) {
    AuthContent(bangla,session.busy,session.error,language,{session.error="";close()},{session.error=""}) { creating,email,password,confirmation,done ->
        if(creating)session.register(email,password,confirmation,done)
        else session.login(email,password,done)
    }
}

@Composable
internal fun AuthContent(
    bangla:Boolean,
    busy:Boolean,
    error:String,
    language:(Boolean)->Unit,
    close:()->Unit,
    clearError:()->Unit,
    submit:(Boolean,String,String,String,()->Unit)->Unit
) {
    fun t(en:String,bn:String)=LocaleStrings.get(en,if(bangla)bn else en)
    var creating by rememberSaveable {mutableStateOf(false)}
    var email by rememberSaveable {mutableStateOf("")}
    // Passwords deliberately stay out of saved instance state, as before.
    var password by remember {mutableStateOf("")}
    var confirmation by remember {mutableStateOf("")}
    var visible by remember {mutableStateOf(false)}
    val keyboard=LocalSoftwareKeyboardController.current
    fun changeMode(value:Boolean) {
        creating=value;password="";confirmation="";visible=false;clearError()
    }
    BackHandler(creating){changeMode(false)}
    val emailError=error=="Enter a valid email address."
    val passwordError=error=="Use at least 6 password characters."||error=="Choose a stronger password (at least 6 characters)."
    val confirmationError=error=="Passwords do not match."
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),contentAlignment=Alignment.TopCenter) {
        Column(Modifier.widthIn(max=600.dp).fillMaxWidth().safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).testTag("auth-content")) {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                IconButton(onClick={if(creating)changeMode(false)else close()},modifier=Modifier.testTag("auth-back")) {
                    Icon(painterResource(R.drawable.ic_back),t("Back","ফিরুন"))
                }
                TextButton(onClick={language(!bangla)},modifier=Modifier.heightIn(min=48.dp)) {Text(if(bangla)"English" else "বাংলা")}
            }
            Box(Modifier.fillMaxWidth().height(148.dp)) {
                Image(painterResource(R.drawable.home_hero),null,Modifier.matchParentSize(),contentScale=ContentScale.Crop)
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent,MaterialTheme.colorScheme.background))))
                BrandLogo(Modifier.align(Alignment.BottomStart).padding(start=16.dp).size(86.dp))
            }
            AnimatedContent(targetState=creating,transitionSpec={
                (fadeIn(tween(220))+slideInHorizontally(tween(240)){it/16}) togetherWith fadeOut(tween(140))
            },label="Account form") { registering ->
                val active=registering==creating
                val emailFocus=remember{FocusRequester()}
                val passwordFocus=remember{FocusRequester()}
                val confirmationFocus=remember{FocusRequester()}
                LaunchedEffect(error,active) {
                    if(active)when {
                        emailError->emailFocus.requestFocus()
                        passwordError->passwordFocus.requestFocus()
                        confirmationError&&registering->confirmationFocus.requestFocus()
                    }
                }
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text(t(if(registering)"Create account" else "Sign in",if(registering)"অ্যাকাউন্ট খুলুন" else "সাইন ইন"),style=MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(email,{email=it},label={Text(t("Email","ইমেইল"))},singleLine=true,enabled=!busy&&active,
                        isError=emailError,supportingText=if(emailError)({AuthError(error)})else null,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email,imeAction=ImeAction.Next),
                        keyboardActions=KeyboardActions(onNext={passwordFocus.requestFocus()}),shape=RoundedCornerShape(14.dp),
                        modifier=Modifier.fillMaxWidth().focusRequester(emailFocus).testTag("auth-email"))
                    val send={
                        if(!busy&&registering==creating) {
                            keyboard?.hide()
                            submit(registering,email,password,confirmation){password="";confirmation=""}
                        }
                    }
                    OutlinedTextField(password,{password=it},label={Text(t("Password","পাসওয়ার্ড"))},singleLine=true,enabled=!busy&&active,
                        visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),isError=passwordError,
                        supportingText=if(passwordError)({AuthError(error)})else null,
                        trailingIcon={TextButton(onClick={visible=!visible},enabled=!busy&&active){Text(t(if(visible)"Hide" else "Show",if(visible)"লুকান" else "দেখুন"))}},
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,imeAction=if(registering)ImeAction.Next else ImeAction.Done),
                        keyboardActions=KeyboardActions(onNext={confirmationFocus.requestFocus()},onDone={send()}),shape=RoundedCornerShape(14.dp),
                        modifier=Modifier.fillMaxWidth().focusRequester(passwordFocus).testTag("auth-password"))
                    if(registering)OutlinedTextField(confirmation,{confirmation=it},label={Text(t("Confirm password","পাসওয়ার্ড নিশ্চিত করুন"))},singleLine=true,enabled=!busy&&active,
                        visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),isError=confirmationError,
                        supportingText=if(confirmationError)({AuthError(error)})else null,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,imeAction=ImeAction.Done),
                        keyboardActions=KeyboardActions(onDone={send()}),shape=RoundedCornerShape(14.dp),
                        modifier=Modifier.fillMaxWidth().focusRequester(confirmationFocus).testTag("auth-confirmation"))
                    if(error.isNotBlank()&&!emailError&&!passwordError&&!confirmationError)AuthError(error)
                    Button(onClick=send,enabled=!busy&&active,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("auth-submit")) {
                        if(busy) {CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=MaterialTheme.colorScheme.onPrimary);Spacer(Modifier.width(8.dp))}
                        Text(t(if(busy)if(registering)"Creating account…" else "Signing in…" else if(registering)"Create account" else "Sign in",
                            if(busy)if(registering)"অ্যাকাউন্ট খোলা হচ্ছে…" else "সাইন ইন হচ্ছে…" else if(registering)"অ্যাকাউন্ট খুলুন" else "সাইন ইন"))
                    }
                    TextButton(onClick={changeMode(!registering)},enabled=!busy&&active,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("auth-toggle")) {
                        Text(t(if(registering)"Already have an account? Sign in" else "New here? Create account",if(registering)"অ্যাকাউন্ট আছে? সাইন ইন" else "নতুন? অ্যাকাউন্ট খুলুন"),textAlign=TextAlign.Center)
                    }
                    TextButton(onClick=close,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("auth-continue")) {
                        Text(t("Continue browsing","ম্যাচ দেখতে থাকুন"),color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthError(error:String) {
    Text(LocaleStrings.get(error),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,
        modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
}
