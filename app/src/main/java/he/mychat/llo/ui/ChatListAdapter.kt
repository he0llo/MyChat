package he.mychat.llo.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import he.mychat.llo.R
import he.mychat.llo.data.ChatTemplate
import he.mychat.llo.ui.theme.MyScheme

class ChatListAdapter(
    private val scheme: MyScheme,
    private val onClick: (ChatTemplate) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.VH>() {

    private val items = mutableListOf<ChatTemplate>()
    private val previews = mutableMapOf<String, String>()
    private val unreads = mutableMapOf<String, Int>()

    fun submit(
        list: List<ChatTemplate>,
        previewMap: Map<String, String>,
        unreadMap: Map<String, Int>
    ) {
        items.clear()
        items.addAll(list)
        previews.clear()
        previews.putAll(previewMap)
        unreads.clear()
        unreads.putAll(unreadMap)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return VH(v)
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
        holder.preview.text = previews[t.folder] ?: "还没有聊天记录"
        holder.preview.setTextColor(scheme.onSurfaceVariant)

        val unread = unreads[t.folder] ?: 0
        holder.badge.visibility = if (unread > 0) View.VISIBLE else View.GONE

        holder.itemView.alpha = if (t.enabled) 1f else 0.45f
        holder.itemView.setOnClickListener { onClick(t) }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: AvatarView = view.findViewById(R.id.avatar)
        val name: TextView = view.findViewById(R.id.name)
        val preview: TextView = view.findViewById(R.id.preview)
        val badge: View = view.findViewById(R.id.badge)
    }
}