package he.mychat.llo.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import he.mychat.llo.R
import he.mychat.llo.ui.theme.MyScheme

class MemoryAdapter(
    private var scheme: MyScheme,
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<MemoryAdapter.VH>() {

    private val items = mutableListOf<String>()

    fun submit(list: List<String>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_memory, parent, false)
        )
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val raw = items[position]
        val parsed = parse(raw)

        if (parsed.first.isNotEmpty()) {
            holder.date.visibility = View.VISIBLE
            holder.date.text = parsed.first
            holder.date.setTextColor(scheme.onSurfaceVariant)
        } else {
            holder.date.visibility = View.GONE
        }
        holder.content.text = parsed.second
        holder.content.setTextColor(scheme.onSurface)

        holder.delete.imageTintList = ColorStateList.valueOf(scheme.onSurfaceVariant)
        holder.delete.setOnClickListener { onDelete(position) }
        holder.row.setOnClickListener { onEdit(position) }

        // 最后一条不显示分割线
        holder.divider.visibility =
            if (position == items.size - 1) View.GONE else View.VISIBLE
        holder.divider.setBackgroundColor(scheme.outlineVariant)
    }

    /**
     * 拆出 "时间: 内容"，时间支持两种：
     *  - 新格式：2026/10.1/20:32（含冒号，需再找一次）
     *  - 旧格式：2026/10.1
     * 找不到时间前缀时，返回 ("", 原文)
     */
    private fun parse(raw: String): Pair<String, String> {
        val idx = raw.indexOf(':')
        if (idx <= 0) return "" to raw

        val head = raw.substring(0, idx).trim()

        // 新格式 2026/10.1/20:32：head 是 "2026/10.1/20"，还差后半段，往后找第二个冒号
        if (head.matches(Regex("""\d+/\d+\.\d+/\d{2}"""))) {
            val idx2 = raw.indexOf(':', idx + 1)
            if (idx2 > 0) {
                val head2 = raw.substring(0, idx2).trim()
                if (head2.matches(Regex("""\d+/\d+\.\d+/\d{2}:\d{2}"""))) {
                    return head2 to raw.substring(idx2 + 1).trim()
                }
            }
        }

        // 旧格式 2026/10.1
        val oldFormat = head.matches(Regex("""\d+/\d+\.\d+"""))
        return if (oldFormat) head to raw.substring(idx + 1).trim()
        else "" to raw
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val row: View = view.findViewById(R.id.row)
        val date: TextView = view.findViewById(R.id.txt_date)
        val content: TextView = view.findViewById(R.id.txt_content)
        val delete: ImageButton = view.findViewById(R.id.btn_delete)
        val divider: View = view.findViewById(R.id.divider)
    }
}