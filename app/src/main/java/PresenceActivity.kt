package SmartSecurity.gatetrackapplication.ui.admin

import SmartSecurity.gatetrackapplication.data.model.EntryLog
import SmartSecurity.gatetrackapplication.databinding.ActivityPresenceBinding
import SmartSecurity.gatetrackapplication.databinding.ItemPresenceBinding
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Locale

data class PresenceRow(
    val title: String,
    val detail: String,
    val badge: String,
    val color: String,
    val header: Boolean = false
)

class PresenceAdapter : RecyclerView.Adapter<PresenceAdapter.VH>() {
    private var rows: List<PresenceRow> = emptyList()

    fun submit(list: List<PresenceRow>) {
        rows = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemPresenceBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPresenceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = rows[position]
        h.b.tvName.text = r.title
        if (r.header) {
            h.b.root.setCardBackgroundColor(Color.parseColor("#DDE6F0"))
            h.b.tvDetail.visibility = View.GONE
            h.b.tvBadge.visibility = View.GONE
        } else {
            h.b.root.setCardBackgroundColor(Color.WHITE)
            h.b.tvDetail.visibility = View.VISIBLE
            h.b.tvBadge.visibility = View.VISIBLE
            h.b.tvDetail.text = r.detail
            h.b.tvBadge.text = r.badge
            h.b.tvBadge.setTextColor(Color.parseColor(r.color))
        }
    }
}

class PresenceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPresenceBinding
    private val adapter = PresenceAdapter()
    private val db = FirebaseFirestore.getInstance()
    private var entryListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null

    private var entries: List<EntryLog> = emptyList()
    private var residents: List<Triple<String, String, String>> = emptyList() // name, unit, presence

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPresenceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvPresence.layoutManager = LinearLayoutManager(this)
        binding.rvPresence.adapter = adapter
        binding.btnBack.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        entryListener = db.collection("entryLogs")
            .whereEqualTo("direction", "ENTRY")
            .addSnapshotListener { snap, _ ->
                entries = snap?.toObjects(EntryLog::class.java) ?: emptyList()
                render()
            }
        userListener = db.collection("users")
            .whereEqualTo("role", "RESIDENT")
            .addSnapshotListener { snap, _ ->
                residents = snap?.documents?.map {
                    Triple(
                        it.getString("fullName").orEmpty(),
                        it.getString("unitNumber").orEmpty(),
                        it.getString("presence") ?: "OUT"
                    )
                }?.sortedBy { it.second } ?: emptyList()
                render()
            }
    }

    override fun onStop() {
        super.onStop()
        entryListener?.remove()
        userListener?.remove()
    }

    private fun render() {
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dayFmt.format(java.util.Date())

        val inside = entries
            .filter {
                it.entryType == "VISITOR" && it.status == "APPROVED" && it.presence != "LEFT"
            }
            .sortedByDescending { it.timestamp?.time ?: Long.MAX_VALUE }

        val left = entries
            .filter {
                it.entryType == "VISITOR" && it.status == "APPROVED" && it.presence == "INSIDE"
            }
            .sortedByDescending { it.exitTime?.time ?: 0L }

        val rows = ArrayList<PresenceRow>()

        // ---------- Visitors inside ----------
        rows.add(PresenceRow("VISITORS INSIDE (${inside.size})", "", "", "", true))
        if (inside.isEmpty()) {
            rows.add(PresenceRow("No visitors inside", "", "", "#6B7A8C", false))
        }
        inside.forEach { e ->
            val sb = StringBuilder("Unit ${e.unitNumber}")
            if (e.plateNumber.isNotBlank()) {
                sb.append("\n${e.vehicleType.ifBlank { "VEHICLE" }}: ${e.plateNumber}")
            }
            if (e.purpose.isNotBlank()) sb.append("\nPurpose: ${e.purpose}")
            e.timestamp?.let { sb.append("\nArrived ${timeFmt.format(it)}") }
            rows.add(PresenceRow(e.visitorName.ifBlank { "Unknown" }, sb.toString(), "INSIDE", "#2E7D32"))
        }

        // ---------- Visitors who left today ----------
        rows.add(PresenceRow("VISITORS WHO LEFT TODAY (${left.size})", "", "", "", true))
        if (left.isEmpty()) {
            rows.add(PresenceRow("Nobody has left yet today", "", "", "#6B7A8C", false))
        }
        left.forEach { e ->
            val sb = StringBuilder("Unit ${e.unitNumber}")
            if (e.plateNumber.isNotBlank()) {
                sb.append("\n${e.vehicleType.ifBlank { "VEHICLE" }}: ${e.plateNumber}")
            }
            if (e.purpose.isNotBlank()) sb.append("\nPurpose: ${e.purpose}")
            val arrived = e.timestamp?.let { timeFmt.format(it) } ?: "--:--"
            val out = e.exitTime?.let { timeFmt.format(it) } ?: "--:--"
            sb.append("\nIn $arrived  •  Out $out")
            rows.add(PresenceRow(e.visitorName.ifBlank { "Unknown" }, sb.toString(), "LEFT", "#546E7A"))
        }

        // ---------- Residents ----------
        val resIn = residents.count { it.third == "INSIDE" }
        val resOut = residents.size - resIn
        rows.add(PresenceRow("RESIDENTS ($resIn in, $resOut out)", "", "", "", true))
        if (residents.isEmpty()) {
            rows.add(PresenceRow("No residents registered", "", "", "#6B7A8C", false))
        }
        residents.forEach { r ->
            val isIn = r.third == "INSIDE"
            rows.add(
                PresenceRow(
                    r.first.ifBlank { "Resident" },
                    "Unit ${r.second}",
                    if (isIn) "IN" else "OUT",
                    if (isIn) "#1565C0" else "#546E7A"
                )
            )
        }

        binding.tvSummary.text =
            "Inside: ${inside.size}  •  Left today: ${left.size}  •  Residents in: $resIn, out: $resOut"
        adapter.submit(rows)
    }}