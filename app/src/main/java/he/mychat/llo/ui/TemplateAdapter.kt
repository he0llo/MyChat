package he.mychat.llo.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import he.mychat.llo.R
import he.mychat.llo.data.ChatTemplate
import he.mychat.llo.ui.theme.MyScheme

class TemplateAdapter(
    private val scheme: MyScheme,
    private val onClick: (ChatTemplate) -> Unit,
    private val onToggle: (ChatTemplate, Boolean) -> Unit
) : RecyclerView.Adapter<TemplateAdapter.VH>() {

    private val items = mutableListOf<ChatTemplate>()

    fun submit(list: List<ChatTemplate>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_template, parent, false)
        )
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = items[position]

        holder.avatar.bind(
            t.avatar.ifBlank { t.name },
            t.avatarPath.ifBlank { null },
            scheme.primaryContainer,
            scheme.onPrimaryContainer
        )
        holder.name.text = t.name
        holder.name.setTextColor(scheme.onSurface)
        holder.prompt.text = t.prompt.replace("\n", " ").take(80)
        holder.prompt.setTextColor(scheme.onSurfaceVariant)

        holder.switch.setOnCheckedChangeListener(null)
        holder.switch.isChecked = t.enabled
        holder.switch.setOnCheckedChangeListener { _, checked ->
            if (t.enabled != checked) onToggle(t, checked)
        }

        // 未启用时整行降低透明度
        holder.itemView.alpha = if (t.enabled) 1f else 0.45f

        holder.itemView.setOnClickListener { onClick(t) }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: AvatarView = view.findViewById(R.id.avatar)
        val name: TextView = view.findViewById(R.id.name)
        val prompt: TextView = view.findViewById(R.id.prompt)
        val switch: MaterialSwitch = view.findViewById(R.id.switch_enabled)
    }
}