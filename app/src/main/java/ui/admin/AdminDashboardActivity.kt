package SmartSecurity.gatetrackapplication.ui.admin

import SmartSecurity.gatetrackapplication.data.model.EntryLog
import SmartSecurity.gatetrackapplication.databinding.ActivityAdminDashboardBinding
import SmartSecurity.gatetrackapplication.ui.auth.LoginActivity
import SmartSecurity.gatetrackapplication.ui.guard.EntryAdapter
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private val adapter = EntryAdapter()
    private var listener: ListenerRegistration? = null
    private var all: List<EntryLog> = emptyList()

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val textFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        binding.tvWelcome.text = "Signed in as ${FirebaseAuth.getInstance().currentUser?.email}"
        binding.rvEntries.layoutManager = LinearLayoutManager(this)
        binding.rvEntries.adapter = adapter

        binding.etSearch.addTextChangedListener { applyFilter() }

        binding.btnAddUser.setOnClickListener {
            startActivity(Intent(this, AddUserActivity::class.java))
        }
        binding.btnUsers.setOnClickListener {
            startActivity(Intent(this, UsersActivity::class.java))
        }
        binding.btnReports.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
        binding.btnPresence.setOnClickListener {
            startActivity(Intent(this, PresenceActivity::class.java))
        }
        binding.btnShifts.setOnClickListener {
            startActivity(Intent(this, ShiftsActivity::class.java))
        }
        binding.btnExpected.setOnClickListener {
            startActivity(Intent(this, ExpectedActivity::class.java))
        }
        binding.btnMonitor.setOnClickListener {
            val open = binding.monitorBox.visibility == View.VISIBLE
            binding.monitorBox.visibility = if (open) View.GONE else View.VISIBLE
            binding.btnMonitor.text = if (open) "MONITORING  ▼" else "MONITORING  ▲"
        }
        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
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
        listener = FirebaseFirestore.getInstance().collection("entryLogs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snap, _ ->
                all = snap?.toObjects(EntryLog::class.java) ?: emptyList()
                updateStats()
                applyFilter()
            }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
    }

    private fun updateStats() {
        val today = dayFmt.format(Date())
        binding.tvToday.text = all.count {
            it.timestamp?.let { d -> dayFmt.format(d) } == today
        }.toString()
        binding.tvPending.text = all.count { it.status == "PENDING" }.toString()
        binding.tvApproved.text = all.count { it.status == "APPROVED" }.toString()
        binding.tvDenied.text = all.count { it.status == "DENIED" }.toString()
    }

    private fun applyFilter() {
        val q = binding.etSearch.text.toString().trim().lowercase()
        val today = dayFmt.format(Date())

        val list = if (q.isEmpty()) {
            all.filter { e ->
                e.timestamp?.let { d -> dayFmt.format(d) } == today
            }
        } else {
            all.filter { e ->
                val day = e.timestamp?.let { dayFmt.format(it) } ?: ""
                val text = e.timestamp?.let { textFmt.format(it) } ?: ""
                listOf(
                    e.visitorName, e.plateNumber, e.unitNumber,
                    e.idNumber, e.guardName, e.purpose, e.status, e.presence, day, text
                ).any { it.lowercase().contains(q) }
            }
        }

        adapter.submit(list)
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }
}