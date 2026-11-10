package bd.arena.prototype

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query

// Public announcements only. Staff write through the separate authenticated admin page.
data class Notice(val id: String, val title: String, val body: String, val updatedAt: Long, val revision: Long) {
    val readKey: String get() = "$id:$revision"
}
data class NoticeFeed(val notices: List<Notice> = emptyList(), val loading: Boolean = true, val cached: Boolean = false, val error: Boolean = false)
val sampleNotice = Notice("demo-welcome", "Your next game starts here", "Explore a mode, find a free practice match and bring your best game. This is a sample announcement; admin updates will appear here when a project is connected.", 0, 1)
fun unreadNoticeCount(notices: List<Notice>, readKeys: Set<String>) = notices.count { it.readKey !in readKeys }

object Notices {
    fun listen(context: Context, callback: (NoticeFeed) -> Unit): () -> Unit {
        if (BuildConfig.NOTICE_BACKEND == "demo") {
            callback(NoticeFeed(listOf(sampleNotice), loading = false))
            return {}
        }
        val db = database(context)
        var previous = emptyList<Notice>()
        val registration = db.collection("notices").whereEqualTo("published", true)
            .orderBy("updatedAt", Query.Direction.DESCENDING).limit(20)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) callback(NoticeFeed(previous, loading = false, cached = true, error = true))
                else if (snapshot != null) {
                    previous = snapshot.documents.mapNotNull { doc ->
                        val title = doc.getString("title") ?: return@mapNotNull null
                        val body = doc.getString("body") ?: return@mapNotNull null
                        Notice(doc.id, title, body, doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0, doc.getLong("revision") ?: 1)
                    }
                    callback(NoticeFeed(previous, loading = false, cached = snapshot.metadata.isFromCache))
                }
            }
        return { registration.remove() }
    }
    @Synchronized internal fun database(context:Context):FirebaseFirestore {
        val app = FirebaseApp.getApps(context).find { it.name == "arena-notices" }
            ?: FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .apply {
                    if (BuildConfig.FIREBASE_STORAGE_BUCKET.isNotEmpty()) {
                        setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                    }
                }.build(), "arena-notices")
        val db = FirebaseFirestore.getInstance(app)
        if (!configured) {
            if (BuildConfig.FIREBASE_USE_EMULATOR) {
                db.useEmulator("10.0.2.2", 8080)
            }
            configured = true
        }
        return db
    }
    private var configured = false
}
