package SmartSecurity.gatetrackapplication.ui.guard

import SmartSecurity.gatetrackapplication.data.model.EntryLog
import SmartSecurity.gatetrackapplication.databinding.ActivityGuardDashboardBinding
import SmartSecurity.gatetrackapplication.ui.admin.PresenceActivity
import SmartSecurity.gatetrackapplication.ui.auth.LoginActivity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class GuardDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuardDashboardBinding
    private val db = FirebaseFirestore.getInstance()
    private val adapter = EntryAdapter()
    private var listener: ListenerRegistration? = null
    private var shiftListener: ListenerRegistration? = null

    private var activeShiftId: String? = null
    private val timeFmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val email get() = FirebaseAuth.getInstance().currentUser?.email ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuardDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            v.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        binding.tvWelcome.text = "Signed in as $email"
        binding.rvEntries.layoutManager = LinearLayoutManager(this)
        binding.rvEntries.adapter = adapter

        binding.btnNewEntry.setOnClickListener {
            if (activeShiftId == null) {
                Toast.makeText(this, "Start your shift first", Toast.LENGTH_LONG).show()
            } else {
                startActivity(Intent(this, NewEntryActivity::class.java))
            }
        }
        binding.btnPresence.setOnClickListener {
            startActivity(Intent(this, PresenceActivity::class.java))
        }
        binding.btnExpected.setOnClickListener {
            startActivity(Intent(this, SmartSecurity.gatetrackapplication.ui.admin.ExpectedActivity::class.java))
        }
        binding.btnShift.setOnClickListener { toggleShift() }
        binding.btnViews.setOnClickListener {
            val open = binding.viewsBox.visibility == View.VISIBLE
            binding.viewsBox.visibility = if (open) View.GONE else View.VISIBLE
            binding.btnViews.text = if (open) "VIEW MORE  ▼" else "VIEW MORE  ▲"
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
    }

    override fun onStart() {
        super.onStart()
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        listener = db.collection("entryLogs")
            .whereGreaterThanOrEqualTo("timestamp", cal.time)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                val list = snap?.toObjects(EntryLog::class.java) ?: emptyList()
                adapter.submit(list)
                binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }

        shiftListener = db.collection("shifts")
            .whereEqualTo("guardId", uid)
            .whereEqualTo("active", true)
            .addSnapshotListener { snap, _ ->
                val doc = snap?.documents?.firstOrNull()
                activeShiftId = doc?.id
                if (doc == null) {
                    showShift(false, "Start your shift to record entries")
                } else {
                    val start = doc.getDate("startTime")
                    val since = start?.let { timeFmt.format(it) } ?: "just now"
                    showShift(true, "On duty since $since")
                }
            }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        shiftListener?.remove()
    }

    private fun showShift(onDuty: Boolean, detail: String) {
        binding.shiftBanner.setBackgroundColor(Color.parseColor(if (onDuty) "#2E7D32" else "#C62828"))
        binding.tvShiftStatus.text = if (onDuty) "ON DUTY" else "OFF DUTY"
        binding.tvShiftDetail.text = detail
        binding.btnShift.text = if (onDuty) "END SHIFT" else "START SHIFT"
    }

    private fun toggleShift() {
        binding.btnShift.isEnabled = false
        val id = activeShiftId
        val task = if (id == null) {
            db.collection("shifts").add(
                hashMapOf(
                    "guardId" to uid,
                    "guardName" to email,
                    "active" to true,
                    "startTime" to FieldValue.serverTimestamp(),
                    "endTime" to null
                )
            )
        } else {
            db.collection("shifts").document(id).update(
                "active", false,
                "endTime", FieldValue.serverTimestamp()
            )
        }
        task.addOnCompleteListener { t ->
            binding.btnShift.isEnabled = true
            if (!t.isSuccessful) {
                Toast.makeText(this, "Shift error: ${t.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}