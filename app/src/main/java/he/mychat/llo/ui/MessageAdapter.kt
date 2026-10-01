package he.mychat.llo.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import he.mychat.llo.R
import he.mychat.llo.data.ChatMessage
import he.mychat.llo.ui.theme.MyScheme

class MessageAdapter(
    private var scheme: MyScheme,
    private var userAvatarText: String = "我",
    private var userAvatarPath: String? = null,
    private var aiAvatarText: String = "友",
    private var aiAvatarPath: String? = null,
    private val onRetry: ((ChatMessage) -> Unit)? = null,
    private val onLongPress: ((DisplayItem) -> Unit)? = null,
    private val onClick: ((DisplayItem) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_TIME = 0
        const val TYPE_USER = 1
        const val TYPE_AI = 2
    }

    data class DisplayItem(
        val type: Int,
        val text: String = "",
        val timeText: String = "",
        val failed: Boolean = false,
        val source: ChatMessage? = null,
        /** 用于选择模式：用户消息 "msg-<time>"，AI 气泡 "msg-<time>-bubble-<idx>" */
        val selectionKey: String = ""
    )

    private val items = mutableListOf<DisplayItem>()

    var selectionMode: Boolean = false
        private set

    private val selectedKeys = mutableSetOf<String>()

    fun submit(list: List<DisplayItem>, selectionMode: Boolean, selected: Set<String>) {
        items.clear()
        items.addAll(list)
        this.selectionMode = selectionMode
        selectedKeys.clear()
        selectedKeys.addAll(selected)
        notifyDataSetChanged()
    }

    fun updateScheme(newScheme: MyScheme) {
        scheme = newScheme
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = items[position].type

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TIME -> TimeVH(inflater.inflate(R.layout.item_message_time, parent, false))
            TYPE_USER -> UserVH(inflater.inflate(R.layout.item_message_user, parent, false))
            else -> AiVH(inflater.inflate(R.layout.item_message_ai, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        val radius = UiUtils.dp(holder.itemView.context, 16)

        // 选中背景
        val selected = item.selectionKey.isNotEmpty() && selectedKeys.contains(item.selectionKey)
        holder.itemView.setBackgroundColor(
            if (selected) scheme.primaryContainer else Color.TRANSPARENT
        )

        // 长按进入选择模式
        holder.itemView.setOnLongClickListener {
            if (!selectionMode && item.source != null && item.selectionKey.isNotEmpty()) {
                onLongPress?.invoke(item)
                true
            } else {
                selectionMode
            }
        }
        // 点击切换选中
        holder.itemView.setOnClickListener {
            if (selectionMode && item.selectionKey.isNotEmpty()) {
                onClick?.invoke(item)
            }
        }

        when (holder) {
            is TimeVH -> {
                holder.time.text = item.timeText
                holder.time.setTextColor(scheme.onSurfaceVariant)
            }
            is UserVH -> {
                holder.bubble.text = item.text
                holder.bubble.background = UiUtils.rounded(scheme.bubbleUser, radius)
                holder.bubble.setTextColor(scheme.onBubbleUser)
                holder.avatar.bind(
                    userAvatarText,
                    userAvatarPath,
                    scheme.primaryContainer,
                    scheme.onPrimaryContainer
                )
                if (item.failed) {
                    holder.error.visibility = View.VISIBLE
                    holder.error.setOnClickListener {
                        item.source?.let { msg -> onRetry?.invoke(msg) }
                    }
                } else {
                    holder.error.visibility = View.GONE
                    holder.error.setOnClickListener(null)
                }
            }
            is AiVH -> {
                holder.bubble.text = item.text
                holder.bubble.background = UiUtils.rounded(scheme.bubbleAi, radius)
                holder.bubble.setTextColor(scheme.onBubbleAi)
                holder.avatar.bind(
                    aiAvatarText,
                    aiAvatarPath,
                    scheme.primaryContainer,
                    scheme.onPrimaryContainer
                )
            }
        }
    }

    class TimeVH(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.time)
    }

    class UserVH(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: TextView = view.findViewById(R.id.bubble)
        val avatar: AvatarView = view.findViewById(R.id.avatar)
        val error: ImageView = view.findViewById(R.id.error_icon)
    }

    class AiVH(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: TextView = view.findViewById(R.id.bubble)
        val avatar: AvatarView = view.findViewById(R.id.avatar)
    }
}