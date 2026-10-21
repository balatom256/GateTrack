package SmartSecurity.gatetrackapplication.ui.admin

import SmartSecurity.gatetrackapplication.databinding.ActivityExpectedBinding
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpectedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExpectedBinding
    private val adapter = PresenceAdapter()
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpectedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvExpected.layoutManager = LinearLayoutManager(this)
        binding.rvExpected.adapter = adapter
        binding.btnBack.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        listener = FirebaseFirestore.getInstance().collection("preApprovals")
            .whereEqualTo("expectedDate", today)
            .addSnapshotListener { snap, _ ->
                val docs = (snap?.documents ?: emptyList())
                    .sortedBy { it.getString("unitNumber").orEmpty() }
                val waiting = docs.filter { it.getBoolean("used") != true }
                val arrived = docs.filter { it.getBoolean("used") == true }

                val rows = ArrayList<PresenceRow>()

                rows.add(PresenceRow("STILL EXPECTED (${waiting.size})", "", "", "", true))
                if (waiting.isEmpty()) {
                    rows.add(PresenceRow("No expected visitors waiting", "", "", "#6B7A8C", false))
                }
                waiting.forEach { d ->
                    val sb = StringBuilder("Visiting unit ${d.getString("unitNumber").orEmpty()}")
                    val p = d.getString("purpose").orEmpty()
                    if (p.isNotBlank()) sb.append("\nPurpose: $p")
                    rows.add(
                        PresenceRow(
                            d.getString("visitorName").orEmpty().ifBlank { "Unknown" },
                            sb.toString(), "EXPECTED", "#EF6C00"
                        )
                    )
                }

                rows.add(PresenceRow("ALREADY ARRIVED (${arrived.size})", "", "", "", true))
                if (arrived.isEmpty()) {
                    rows.add(PresenceRow("Nobody has arrived yet", "", "", "#6B7A8C", false))
                }
                arrived.forEach { d ->
                    val sb = StringBuilder("Visited unit ${d.getString("unitNumber").orEmpty()}")
                    val p = d.getString("purpose").orEmpty()
                    if (p.isNotBlank()) sb.append("\nPurpose: $p")
                    rows.add(
                        PresenceRow(
                            d.getString("visitorName").orEmpty().ifBlank { "Unknown" },
                            sb.toString(), "ARRIVED", "#2E7D32"
                        )
                    )
                }

                binding.tvSummary.text =
                    "Today: ${docs.size}  •  Waiting: ${waiting.size}  •  Arrived: ${arrived.size}"
                adapter.submit(rows)
            }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
    }
}