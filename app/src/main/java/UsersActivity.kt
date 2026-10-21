package SmartSecurity.gatetrackapplication.ui.admin

import SmartSecurity.gatetrackapplication.databinding.ActivityUsersBinding
import SmartSecurity.gatetrackapplication.databinding.ItemUserBinding
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class UserAdapter(
    private val onToggle: (DocumentSnapshot, Boolean) -> Unit
) : RecyclerView.Adapter<UserAdapter.VH>() {

    private var items: List<DocumentSnapshot> = emptyList()

    fun submit(list: List<DocumentSnapshot>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemUserBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val d = items[position]
        val role = d.getString("role").orEmpty()
        val unit = d.getString("unitNumber").orEmpty()
        h.b.tvName.text = d.getString("fullName").orEmpty().ifBlank { "Unnamed" }
        h.b.tvDetail.text = buildString {
            append(role)
            if (role == "RESIDENT") append(" • Unit $unit")
            append("\n${d.getString("email").orEmpty()}")
            val phone = d.getString("phone").orEmpty()
            if (phone.isNotBlank()) append("\n$phone")
        }
        h.b.swActive.setOnCheckedChangeListener(null)
        h.b.swActive.isChecked = d.getBoolean("active") != false
        h.b.swActive.setOnCheckedChangeListener { _, checked -> onToggle(d, checked) }
    }
}

class UsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsersBinding
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    private val adapter = UserAdapter { doc, active ->
        db.collection("users").document(doc.id).update("active", active)
            .addOnFailureListener {
                Toast.makeText(this, "Could not update: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.rvUsers.layoutManager = LinearLayoutManager(this)
        binding.rvUsers.adapter = adapter
        binding.btnBack.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        listener = db.collection("users").addSnapshotListener { snap, _ ->
            val docs = (snap?.documents ?: emptyList())
                .filter { it.getString("role") != "ADMIN" }
                .sortedWith(compareBy({ it.getString("role") }, { it.getString("unitNumber") }))
            adapter.submit(docs)
            val off = docs.count { it.getBoolean("active") == false }
            binding.tvSummary.text = "Users: ${docs.size}  •  Deactivated: $off"
        }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
    }
}