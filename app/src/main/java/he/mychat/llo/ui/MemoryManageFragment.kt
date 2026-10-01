package he.mychat.llo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import he.mychat.llo.R
import he.mychat.llo.TopBarHost
import he.mychat.llo.data.ChatTemplate
import he.mychat.llo.data.TemplateRepository
import he.mychat.llo.ui.theme.MyScheme
import he.mychat.llo.ui.theme.ThemeEngine
import org.json.JSONArray
import java.util.Calendar

class MemoryManageFragment : Fragment(R.layout.fragment_memory_manage) {

    companion object {
        private const val ARG_FOLDER = "folder"

        fun newInstance(folder: String): MemoryManageFragment {
            val f = MemoryManageFragment()
            f.arguments = Bundle().apply { putString(ARG_FOLDER, folder) }
            return f
        }
    }

    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var adapter: MemoryAdapter
    private lateinit var template: ChatTemplate

    private val notes = mutableListOf<String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scheme = ThemeEngine.scheme(requireContext())
        view.setBackgroundColor(scheme.surface)

        val folder = requireArguments().getString(ARG_FOLDER) ?: return
        template = TemplateRepository.find(requireContext(), folder) ?: run {
            Toast.makeText(requireContext(), "模板不存在", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        recycler = view.findViewById(R.id.recycler)
        empty = view.findViewById(R.id.empty)
        empty.setTextColor(scheme.onSurfaceVariant)

        adapter = MemoryAdapter(
            scheme = scheme,
            onEdit = { pos -> showEditSheet(pos) },
            onDelete = { pos -> deleteMemory(pos) }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            showAddSheet()
        }

        view.findViewById<MaterialButton>(R.id.btn_json).setOnClickListener {
            showJsonDialog()
        }

        (activity as? TopBarHost)?.configureTopBar(
            title = "管理记忆",
            showLeft = true
        )

        reload()
        ThemeEngine.tint(view, scheme)
    }

    private fun reload() {
        notes.clear()
        notes.addAll(TemplateRepository.readNotes(requireContext(), template.folder))
        adapter.submit(notes)
        empty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun save() {
        TemplateRepository.writeNotes(requireContext(), template.folder, notes)
    }

    // ==================== 添加记忆 ====================

    private fun showAddSheet() {
        val ctx = requireContext()
        val scheme = ThemeEngine.scheme(ctx)

        val sheetView = LayoutInflater.from(ctx)
            .inflate(R.layout.sheet_memory_input, null)

        val title = sheetView.findViewById<TextView>(R.id.sheet_title)
        val inputLayout = sheetView.findViewById<TextInputLayout>(R.id.input_layout)
        val input = sheetView.findViewById<TextInputEditText>(R.id.input_content)
        val hintTime = sheetView.findViewById<TextView>(R.id.hint_time)
        val btnCancel = sheetView.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnSave = sheetView.findViewById<MaterialButton>(R.id.btn_save)

        title.text = "添加记忆"
        title.setTextColor(scheme.onSurface)

        val autoTime = nowStamp()
        hintTime.text = "将以 「$autoTime:内容」 的格式保存"
        hintTime.setTextColor(scheme.onSurfaceVariant)

        inputLayout.boxStrokeColor = scheme.outline
        inputLayout.hintTextColor =
            android.content.res.ColorStateList.valueOf(scheme.onSurfaceVariant)
        inputLayout.defaultHintTextColor =
            android.content.res.ColorStateList.valueOf(scheme.onSurfaceVariant)
        input.setTextColor(scheme.onSurface)

        val dialog = BottomSheetDialog(ctx).apply {
            setContentView(sheetView)
            setCanceledOnTouchOutside(true)
        }
        setupBottomSheet(dialog, sheetView, scheme)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            var text = input.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(ctx, "内容不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val hasTimePrefix =
                text.matches(Regex("""^\d+/\d+\.\d+/\d{2}:\d{2}:.*"""))
            if (!hasTimePrefix) {
                text = "$autoTime:$text"
            }

            notes.add(text)
            adapter.submit(notes)
            save()
            empty.visibility = View.GONE
            dialog.dismiss()

            recycler.post {
                val last = adapter.itemCount - 1
                if (last >= 0) recycler.scrollToPosition(last)
            }
        }

        dialog.show()
    }

    // ==================== 编辑记忆 ====================

    private fun showEditSheet(position: Int) {
        val ctx = requireContext()
        val scheme = ThemeEngine.scheme(ctx)
        val current = notes.getOrNull(position) ?: return

        val sheetView = LayoutInflater.from(ctx)
            .inflate(R.layout.sheet_memory_input, null)

        val title = sheetView.findViewById<TextView>(R.id.sheet_title)
        val inputLayout = sheetView.findViewById<TextInputLayout>(R.id.input_layout)
        val input = sheetView.findViewById<TextInputEditText>(R.id.input_content)
        val hintTime = sheetView.findViewById<TextView>(R.id.hint_time)
        val btnCancel = sheetView.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnSave = sheetView.findViewById<MaterialButton>(R.id.btn_save)

        title.text = "编辑记忆"
        title.setTextColor(scheme.onSurface)

        val parsed = parseRaw(current)
        if (parsed.first.isNotEmpty()) {
            input.setText(parsed.second)
            hintTime.text = "时间保持为 ${parsed.first}"
        } else {
            val autoTime = nowStamp()
            input.setText(current)
            hintTime.text = "将以 「$autoTime:内容」 的格式保存"
        }
        input.setSelection(input.text?.length ?: 0)
        hintTime.setTextColor(scheme.onSurfaceVariant)

        inputLayout.boxStrokeColor = scheme.outline
        inputLayout.hintTextColor =
            android.content.res.ColorStateList.valueOf(scheme.onSurfaceVariant)
        inputLayout.defaultHintTextColor =
            android.content.res.ColorStateList.valueOf(scheme.onSurfaceVariant)
        input.setTextColor(scheme.onSurface)

        val dialog = BottomSheetDialog(ctx).apply {
            setContentView(sheetView)
            setCanceledOnTouchOutside(true)
        }
        setupBottomSheet(dialog, sheetView, scheme)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(ctx, "内容不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val saved = if (parsed.first.isNotEmpty()) {
                "${parsed.first}:$text"
            } else {
                "${nowStamp()}:$text"
            }

            notes[position] = saved
            adapter.submit(notes)
            save()
            dialog.dismiss()
        }

        dialog.show()
    }

    // ==================== BottomSheet 圆角 + inset ====================

    private fun setupBottomSheet(
        dialog: BottomSheetDialog,
        sheetView: View,
        scheme: MyScheme
    ) {
        val ctx = requireContext()
        val basePadBottom = UiUtils.dp(ctx, 20)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) as? FrameLayout ?: return@setOnShowListener

            // 顶部圆角 + 主题填充色
            val radius = UiUtils.dp(ctx, 28).toFloat()
            val shape = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(radius)
                .setTopRightCornerSize(radius)
                .setBottomLeftCornerSize(0f)
                .setBottomRightCornerSize(0f)
                .build()

            val bg = MaterialShapeDrawable(shape).apply {
                fillColor = android.content.res.ColorStateList.valueOf(scheme.surfaceContainerLow)
            }
            bottomSheet.background = bg

            // 内容延伸到底部，导航栏/IME 用 padding 让开
            ViewCompat.setOnApplyWindowInsetsListener(sheetView) { v, insets ->
                val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val bottom = maxOf(nav, ime)
                v.setPadding(
                    v.paddingLeft,
                    v.paddingTop,
                    v.paddingRight,
                    basePadBottom + bottom
                )
                insets
            }
            ViewCompat.requestApplyInsets(sheetView)
        }
    }

    // ==================== 删除 ====================

    private fun deleteMemory(position: Int) {
        if (position !in notes.indices) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除记忆")
            .setMessage("确定删除这条记忆吗？")
            .setPositiveButton("删除") { _, _ ->
                notes.removeAt(position)
                adapter.submit(notes)
                save()
                empty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== JSON ====================

    private fun showJsonDialog() {
        val ctx = requireContext()

        val container = LayoutInflater.from(ctx)
            .inflate(R.layout.dialog_json_edit, null)

        val input = container.findViewById<EditText>(R.id.input_json)
        input.setText(buildPrettyJson())
        input.setTextColor(ThemeEngine.scheme(ctx).onSurface)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("记忆 JSON")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                applyJson(input.text.toString())
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("重新格式化") { _, _ ->
                val parsed = tryParse(input.text.toString())
                if (parsed != null) {
                    val pretty = JSONArray().apply {
                        parsed.forEach { put(it) }
                    }.toString(2)
                    input.setText(pretty)
                    input.setSelection(pretty.length)
                } else {
                    Toast.makeText(ctx, "JSON 格式错误", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun buildPrettyJson(): String {
        val arr = JSONArray()
        notes.forEach { arr.put(it) }
        return arr.toString(2)
    }

    private fun tryParse(text: String): List<String>? {
        return try {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return emptyList()
            val arr = JSONArray(trimmed)
            val result = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val v = arr.optString(i, "")
                if (v.isNotBlank()) result.add(v)
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    private fun applyJson(text: String) {
        val parsed = tryParse(text)
        if (parsed == null) {
            Toast.makeText(requireContext(), "JSON 格式错误，未保存", Toast.LENGTH_SHORT).show()
            return
        }
        notes.clear()
        notes.addAll(parsed)
        adapter.submit(notes)
        save()
        empty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
    }

    // ==================== 工具 ====================

    /** 精确到分钟，例：2026/10.1/20:32 */
    private fun nowStamp(): String {
        val c = Calendar.getInstance()
        val y = c.get(Calendar.YEAR)
        val mo = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)
        val h = c.get(Calendar.HOUR_OF_DAY)
        val mi = c.get(Calendar.MINUTE)
        return "%d/%d.%d/%02d:%02d".format(y, mo, d, h, mi)
    }

    /**
     * 拆出 "时间:内容"。时间支持：
     *  - 新格式 2026/10.1/20:32（含冒号）
     *  - 旧格式 2026/10.1
     */
    private fun parseRaw(raw: String): Pair<String, String> {
        val idx = raw.indexOf(':')
        if (idx <= 0) return "" to raw

        val head = raw.substring(0, idx).trim()
        if (head.matches(Regex("""\d+/\d+\.\d+/\d{2}"""))) {
            val idx2 = raw.indexOf(':', idx + 1)
            if (idx2 > 0) {
                val head2 = raw.substring(0, idx2).trim()
                if (head2.matches(Regex("""\d+/\d+\.\d+/\d{2}:\d{2}"""))) {
                    return head2 to raw.substring(idx2 + 1).trim()
                }
            }
        }

        val oldFormat = head.matches(Regex("""\d+/\d+\.\d+"""))
        return if (oldFormat) head to raw.substring(idx + 1).trim()
        else "" to raw
    }
}