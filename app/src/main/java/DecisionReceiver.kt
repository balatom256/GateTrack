package SmartSecurity.gatetrackapplication.ui.resident

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.firestore.FirebaseFirestore

class DecisionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val logId = intent.getStringExtra("logId") ?: return
        val status = intent.getStringExtra("status") ?: return
        val nid = intent.getIntExtra("nid", 0)

        FirebaseFirestore.getInstance().collection("entryLogs").document(logId)
            .update("status", status)

        context.getSystemService(NotificationManager::class.java).cancel(nid)
    }
}