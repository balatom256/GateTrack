package SmartSecurity.gatetrackapplication.ui.resident
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import SmartSecurity.gatetrackapplication.data.model.EntryLog
import SmartSecurity.gatetrackapplication.databinding.ActivityResidentDashboardBinding
import SmartSecurity.gatetrackapplication.ui.auth.LoginActivity
import android.content.Intent
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ResidentDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResidentDashboardBinding
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null
    private var presenceListener: ListenerRegistration? = null
    private val alerted = HashSet<String>()
    private var firstLoad = true
    private val askNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startAlerts() }

    private fun startAlerts() {
        ContextCompat.startForegroundService(this, Intent(this, VisitorAlertService::class.java))
    }

    private val adapter = ResidentEntryAdapter { log, status -> decide(log, status) }

    private fun decide(log: EntryLog, status: String) {
        db.collection("entryLogs").document(log.logId)
            .update("status", status)
            .addOnFailureListener {
                Toast.makeText(this, "Could not update: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResidentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            v.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        binding.tvWelcome.text = "Signed in as ${FirebaseAuth.getInstance().currentUser?.email}"
        binding.rvEntries.layoutManager = LinearLayoutManager(this)
        binding.rvEntries.adapter = adapter

        binding.btnPreApprove.setOnClickListener {
            startActivity(Intent(this, PreApprovalActivity::class.java))
        }
        binding.btnLogout.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Log out?")
                .setMessage("You will need to sign in again.")
                .setPositiveButton("Log out") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startAlerts()
        }
    }

    override fun onStart() {
        super.onStart()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        firstLoad = true

        presenceListener = db.collection("users").document(uid)
            .addSnapshotListener { doc, _ ->
                val inside = doc?.getString("presence") == "INSIDE"
                binding.tvPresence.text = if (inside) "● You are INSIDE" else "● You are OUT"
            }

        listener = db.collection("entryLogs")
            .whereEqualTo("residentId", uid)
            .addSnapshotListener { snap, _ ->
                val list = (snap?.toObjects(EntryLog::class.java) ?: emptyList())
                    .sortedByDescending { it.timestamp?.time ?: Long.MAX_VALUE }
                adapter.submit(list)
                binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

                val pending = list.filter { it.status == "PENDING" }
                if (firstLoad) {
                    pending.forEach { alerted.add(it.logId) }
                    firstLoad = false
                } else {
                    pending.filter { alerted.add(it.logId) }.forEach { alertNew(it) }
                }
            }
    }

    private fun alertNew(log: EntryLog) {
        try {
            val tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(this, tone).play()
            @Suppress("DEPRECATION")
            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }

        val who = log.visitorName.ifBlank { log.plateNumber.ifBlank { "A visitor" } }
        AlertDialog.Builder(this)
            .setTitle("There is a visitor at the gate")
            .setMessage(
                "$who is at the gate" +
                        if (log.purpose.isNotBlank()) "\nPurpose: ${log.purpose}" else ""
            )
            .setPositiveButton("Approve") { _, _ -> decide(log, "APPROVED") }
            .setNegativeButton("Deny") { _, _ -> decide(log, "DENIED") }
            .setCancelable(false)
            .show()
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        presenceListener?.remove()
    }
}