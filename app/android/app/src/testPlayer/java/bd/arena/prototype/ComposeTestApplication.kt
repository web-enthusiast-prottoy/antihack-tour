package bd.arena.prototype

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import org.robolectric.Shadows.shadowOf

class ComposeTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        shadowOf(packageManager).addActivityIfNotPresent(ComponentName(this, ComponentActivity::class.java))
    }
}
