package SmartSecurity.gatetrackapplication.ui.admin

import SmartSecurity.gatetrackapplication.databinding.ActivityShiftsBinding
import SmartSecurity.gatetrackapplication.databinding.ItemShiftBinding
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShiftAdapter : RecyclerView.Adapter<ShiftAdapter.VH>() {
    private var items: List<DocumentSnapshot> = emptyList()
    private val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

    fun submit(list: List<DocumentSnapshot>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemShiftBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemShiftBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val d = items[position]
        val start: Date? = d.getDate("startTime")
        val end: Date? = d.getDate("endTime")
        val active = d.getBoolean("active") == true

        h.b.tvGuard.text = d.getString("guardName").orEmpty()
        val sb = StringBuilder("Start: ${start?.let { fmt.format(it) } ?: "--"}")
        if (end != null) {
            sb.append("\nEnd: ${fmt.format(end)}")
            if (start != null) {
                val mins = (end.time - start.time) / 60000
                sb.append("\nDuration: ${mins / 60}h ${mins % 60}m")
            }
        }
        h.b.tvTimes.text = sb.toString()
        h.b.tvBadge.text = if (active) "ON DUTY" else "ENDED"
        h.b.tvBadge.setTextColor(Color.parseColor(if (active) "#2E7D32" else "#546E7A"))
    }
}

class ShiftsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShiftsBinding
    private val adapter = ShiftAdapter()
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShiftsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvShifts.layoutManager = LinearLayoutManager(this)
        binding.rvShifts.adapter = adapter
        binding.btnBack.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        listener = FirebaseFirestore.getInstance().collection("shifts")
            .orderBy("startTime", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, _ ->
                val docs = snap?.documents ?: emptyList()
                adapter.submit(docs)
                val on = docs.count { it.getBoolean("active") == true }
                binding.tvSummary.text = "On duty now: $on   •   Shifts listed: ${docs.size}"
            }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
    }
}