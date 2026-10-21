package SmartSecurity.gatetrackapplication.ui.resident

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class VisitorAlertService : Service() {

    private var reg: ListenerRegistration? = null
    private var first = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels()
        startForeground(1, ongoingNotification())

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        reg?.remove()
        first = true
        reg = FirebaseFirestore.getInstance().collection("entryLogs")
            .whereEqualTo("residentId", uid)
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                if (first) {
                    first = false
                    return@addSnapshotListener
                }
                snap.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .forEach { change ->
                        val d = change.document
                        val who = d.getString("visitorName").orEmpty()
                            .ifBlank { d.getString("plateNumber").orEmpty().ifBlank { "A visitor" } }
                        val purpose = d.getString("purpose").orEmpty()
                        notifyVisitor(
                            d.id,
                            "Let the visitor in?",
                            "$who is at your gate" +
                                    (if (purpose.isNotBlank()) " ($purpose)" else "") +
                                    ". Tap to open, or choose below."
                        )
                    }
            }
        return START_STICKY
    }

    override fun onDestroy() {
        reg?.remove()
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0,
            Intent(this, ResidentDashboardActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun decisionIntent(logId: String, status: String, nid: Int, code: Int): PendingIntent {
        val i = Intent(this, DecisionReceiver::class.java)
            .putExtra("logId", logId)
            .putExtra("status", status)
            .putExtra("nid", nid)
        return PendingIntent.getBroadcast(
            this, code, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ongoingNotification() =
        NotificationCompat.Builder(this, "gate_monitor")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("GateTrack")
            .setContentText("Gate alerts are on. You will be told when a visitor arrives")
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .build()

    private fun notifyVisitor(logId: String, title: String, text: String) {
        val nid = logId.hashCode()
        val n = NotificationCompat.Builder(this, "visitor_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(
                android.R.drawable.ic_menu_send, "LET IN",
                decisionIntent(logId, "APPROVED", nid, nid * 2)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel, "DENY",
                decisionIntent(logId, "DENIED", nid, nid * 2 + 1)
            )
            .build()
        getSystemService(NotificationManager::class.java).notify(nid, n)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel("gate_monitor", "Gate monitor", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel("visitor_alerts", "Visitor alerts", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }
}