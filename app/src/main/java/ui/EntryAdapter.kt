package SmartSecurity.gatetrackapplication.ui.guard

import SmartSecurity.gatetrackapplication.data.model.EntryLog
import SmartSecurity.gatetrackapplication.databinding.ItemEntryBinding
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class EntryAdapter(
    private val onAllow: ((EntryLog) -> Unit)? = null
) : RecyclerView.Adapter<EntryAdapter.VH>() {

    private var items: List<EntryLog> = emptyList()
    private val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    private val timeOnly = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submit(list: List<EntryLog>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemEntryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]

        holder.b.tvName.text = e.visitorName.ifBlank { e.plateNumber.ifBlank { "Unknown" } }

        val sb = StringBuilder("${e.entryType} • ${e.direction} • Unit ${e.unitNumber}")
        if (e.plateNumber.isNotBlank()) sb.append("\n${e.vehicleType.ifBlank { "VEHICLE" }}: ${e.plateNumber}")
        if (e.purpose.isNotBlank()) sb.append("\nPurpose: ${e.purpose}")
        if (e.preApproved) sb.append("\nExpected visitor (pre-registered)")
        holder.b.tvDetail.text = sb.toString()

        val label: String
        val color: String
        var allow = false
        when {
            e.entryType == "RESIDENT" && e.direction == "ENTRY" -> { label = "RESIDENT IN"; color = "#1565C0" }
            e.entryType == "RESIDENT" -> { label = "RESIDENT OUT"; color = "#546E7A" }
            e.status == "PENDING" -> { label = "WAITING"; color = "#EF6C00" }
            e.status == "DENIED" -> { label = "DENIED"; color = "#C62828" }
            e.presence == "LEFT" -> { label = "LEFT"; color = "#546E7A" }
            e.presence == "INSIDE" -> { label = "INSIDE"; color = "#2E7D32" }
            else -> { label = "ALLOW VISITOR"; color = "#2E7D32"; allow = true }
        }
        holder.b.tvStatus.text = label
        holder.b.tvStatus.setTextColor(Color.parseColor(color))

        val t = e.timestamp?.let { fmt.format(it) } ?: "just now"
        holder.b.tvTime.text =
            if (e.exitTime != null) "$t\nout ${timeOnly.format(e.exitTime)}" else t

        holder.b.root.setOnClickListener(
            if (allow && onAllow != null) View.OnClickListener { onAllow.invoke(e) } else null
        )
    }
}