package bd.arena.prototype

import android.content.Context
import androidx.compose.runtime.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

internal fun canOpenAdmin(signedIn: Boolean, serverConfirmed: Boolean, admin: Boolean) = signedIn && serverConfirmed && admin

internal class AdminSession(context: Context, private val verifyStaff: Boolean = true) {
    private val app = FirebaseApp.getApps(context).find { it.name == "arena-native" }
        ?: FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .apply {
                if (BuildConfig.FIREBASE_STORAGE_BUCKET.isNotEmpty()) {
                    setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                }
            }.build(), "arena-native")
    val auth = FirebaseAuth.getInstance(app)
    val db = FirebaseFirestore.getInstance(app)
    var restoring by mutableStateOf(true); private set
    var uid by mutableStateOf<String?>(null); private set
    var allowed by mutableStateOf(false); private set
    var owner by mutableStateOf(false); private set
    var checking by mutableStateOf(false); private set
    var accessError by mutableStateOf(false); private set
    var error by mutableStateOf("")
    var busy by mutableStateOf(false)
    private var role: ListenerRegistration? = null
    private val listener = FirebaseAuth.AuthStateListener { a ->
        restoring = false; role?.remove(); allowed = false; owner = false; uid = a.currentUser?.uid
        a.currentUser?.let { user ->
            user.email?.let { email ->
                val profile=db.document("users/${user.uid}")
                db.runTransaction { tx ->
                    val existing=tx.get(profile)
                    if(existing.getString("email")!=email)tx.set(profile,
                        if(existing.exists())mapOf("email" to email)else mapOf("email" to email,"ffUsername" to "","ffUid" to ""),
                        com.google.firebase.firestore.SetOptions.merge())
                }
            }
        }
        accessError = false
        checking = verifyStaff && uid != null
        uid?.takeIf { verifyStaff }?.let { id -> role = db.collection("staffRoles").document(id)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, failure ->
                if (uid != id) return@addSnapshotListener
                val confirmed = snap != null && !snap.metadata.isFromCache && !snap.metadata.hasPendingWrites()
                checking = failure == null && !confirmed
                allowed = canOpenAdmin(auth.currentUser?.uid == id, confirmed, snap?.getBoolean("admin") == true)
                owner = allowed && snap?.getBoolean("owner") == true
                accessError = failure != null
            }
        }
    }
    init {
        if (!configured) {
            if (BuildConfig.FIREBASE_USE_EMULATOR) {
                auth.useEmulator("10.0.2.2", 9099)
                db.useEmulator("10.0.2.2", 8080)
            }
            configured = true
        }
        auth.addAuthStateListener(listener)
    }
    fun close() { role?.remove(); auth.removeAuthStateListener(listener) }
    fun login(email: String, password: String, done: () -> Unit) {
        if (busy) return
        val invalid=registrationError(email,password,password)
        if(invalid!=null){error=invalid;return}
        busy = true; error = ""
        auth.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener {
            busy = false
            if (!it.isSuccessful) error = accountError(it.exception, false)
            else done()
        }
    }
    fun register(email: String, password: String, confirmation: String, done: () -> Unit) {
        if (busy) return
        val invalid = registrationError(email, password, confirmation)
        if (invalid != null) { error = invalid; return }
        busy = true; error = ""
        // Firebase signs the new user in; registration never writes a staff role or wallet.
        auth.createUserWithEmailAndPassword(email.trim(), password).addOnCompleteListener {
            busy = false
            if (!it.isSuccessful) error = accountError(it.exception, true)
            else done()
        }
    }
    companion object { private var configured = false }
}

internal fun registrationError(email: String, password: String, confirmation: String): String? = when {
    !email.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) -> "Enter a valid email address."
    password.length < 6 -> "Use at least 6 password characters."
    password != confirmation -> "Passwords do not match."
    else -> null
}
private fun accountError(error: Exception?, registering: Boolean): String = when (error) {
    is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "This email already has an account. Sign in."
    is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "Choose a stronger password (at least 6 characters)."
    is com.google.firebase.FirebaseNetworkException -> "Connection unavailable. Check the local service and retry."
    is com.google.firebase.FirebaseTooManyRequestsException -> "Too many attempts. Try again later."
    else -> if (registering) "Account creation failed. Check email and connection, then retry." else "Sign in failed. Check email, password and connection."
}

