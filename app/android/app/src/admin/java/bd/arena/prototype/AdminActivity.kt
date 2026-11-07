package bd.arena.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

class AdminActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF101311),
                surface = Color(0xFF1B211D),
                surfaceVariant = Color(0xFF273029),
                onBackground = Color(0xFFF1F3ED),
                onSurface = Color(0xFFF1F3ED),
                onSurfaceVariant = Color(0xFFABB5AD),
                primary = Color(0xFFC4DF9B),
                onPrimary = Color(0xFF18200F),
                secondaryContainer = Color(0xFF34432A),
                onSecondaryContainer = Color(0xFFDDEBCF),
                outline = Color(0xFF788379),
                outlineVariant = Color(0xFF364239)
            )) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AdminApp()
                }
            }
        }
    }
}

@Composable
private fun AdminApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("settings", 0) }
    var bangla by rememberSaveable { mutableStateOf(preferences.getBoolean("bangla", false)) }
    val language = {
        bangla = !bangla
        preferences.edit().putBoolean("bangla", bangla).apply()
    }
    LocaleStrings.configure(context, bangla)
    val session = remember { AdminSession(context) }
    DisposableEffect(session) { onDispose { session.close() } }
    val signOut = { session.error = ""; session.auth.signOut() }

    LocalizedTypography(bangla) {
        if (session.allowed) {
            key(session.uid) { NativeAdmin(session, bangla, language, signOut) }
        } else {
            AdminSignIn(session, bangla, language, signOut)
        }
    }
}

@Composable
private fun AdminSignIn(session: AdminSession, bangla: Boolean, language: () -> Unit, signOut: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(painterResource(R.drawable.admin_brand_logo), null, Modifier.size(64.dp))
            Text("Antihack Admin", style = MaterialTheme.typography.headlineMedium)
            if (BuildConfig.LOCAL_OPERATIONS) Text(adminText("Local sample data"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                session.restoring || session.checking -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(adminText("Verifying staff access"))
                    if (!session.restoring) {
                        Text(adminText("Go online to verify access."), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = signOut) { Text(adminText("Sign out")) }
                    }
                }
                session.uid != null -> {
                    Text(adminText(if (session.accessError) "Could not verify staff access" else "Staff access required"))
                    Button(onClick = signOut) { Text(adminText("Sign out")) }
                }
                else -> {
                    OutlinedTextField(
                        email, { email = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text(adminText("Email")) }, singleLine = true, enabled = !session.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    OutlinedTextField(
                        password, { password = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text(adminText("Password")) }, singleLine = true, enabled = !session.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (session.error.isNotBlank()) Text(adminText(session.error), color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = { session.login(email, password) { password = "" } },
                        enabled = !session.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(adminText(if (session.busy) "Please wait…" else "Sign in")) }
                }
            }
            TextButton(onClick = language) { Text(if (bangla) "English" else "বাংলা") }
        }
    }
}
