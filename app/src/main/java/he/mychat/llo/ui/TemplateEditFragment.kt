package he.mychat.llo.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import he.mychat.llo.R
import he.mychat.llo.TopBarHost
import he.mychat.llo.data.ChatTemplate
import he.mychat.llo.data.DefaultPrompts
import he.mychat.llo.data.StorageManager
import he.mychat.llo.data.TemplateRepository
import he.mychat.llo.ui.theme.ThemeEngine

class TemplateEditFragment : Fragment(R.layout.fragment_template_edit) {

    companion object {
        private const val ARG_FOLDER = "folder"

        fun newInstance(folder: String?): TemplateEditFragment {
            val f = TemplateEditFragment()
            f.arguments = Bundle().apply { putString(ARG_FOLDER, folder) }
            return f
        }
    }

    private var existing: ChatTemplate? = null
    private var pendingAvatarPath: String = ""
    private var preview: AvatarView? = null

    private val pickAvatar = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val prefix = existing?.folder ?: "template"
        val path = StorageManager.importAvatar(requireContext(), uri, prefix)
        if (path.isNullOrBlank()) {
            Toast.makeText(requireContext(), "导入图片失败", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        pendingAvatarPath = path
        val scheme = ThemeEngine.scheme(requireContext())
        preview?.bind("", path, scheme.primaryContainer, scheme.onPrimaryContainer)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scheme = ThemeEngine.scheme(requireContext())
        view.setBackgroundColor(scheme.surface)

        val folder = requireArguments().getString(ARG_FOLDER)
        existing = folder?.let { TemplateRepository.find(requireContext(), it) }

        val inputName = view.findViewById<EditText>(R.id.input_name)
        val inputAvatar = view.findViewById<EditText>(R.id.input_avatar)
        val inputPrompt = view.findViewById<EditText>(R.id.input_prompt)
        val btnPick = view.findViewById<MaterialButton>(R.id.btn_pick_avatar)
        val btnDefault = view.findViewById<MaterialButton>(R.id.btn_default)
        val btnSave = view.findViewById<MaterialButton>(R.id.btn_save)
        val btnDelete = view.findViewById<MaterialButton>(R.id.btn_delete)
        preview = view.findViewById(R.id.avatar_preview)

        existing?.let {
            inputName.setText(it.name)
            inputAvatar.setText(it.avatar)
            inputPrompt.setText(it.prompt)
            pendingAvatarPath = it.avatarPath
        }
        if (pendingAvatarPath.isBlank() && existing == null) {
            pendingAvatarPath = ""
        }

        preview?.bind(
            existing?.avatar ?: "新",
            pendingAvatarPath.ifBlank { null },
            scheme.primaryContainer,
            scheme.onPrimaryContainer
        )

        btnDelete.visibility = if (existing == null) View.GONE else View.VISIBLE

        btnPick.setOnClickListener {
            pickAvatar.launch("image/*")
        }

        btnDefault.setOnClickListener {
            inputPrompt.setText(DefaultPrompts.AI_GIRLFRIEND)
        }

        btnSave.setOnClickListener {
            val name = inputName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "名称不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val avatarText = inputAvatar.text.toString().trim()
            val prompt = inputPrompt.text.toString()

            val cur = existing
            if (cur == null) {
                // 先创建，再把头像路径补上
                val created = TemplateRepository.create(requireContext(), name, avatarText, prompt)
                if (pendingAvatarPath.isNotBlank()) {
                    TemplateRepository.update(
                        requireContext(),
                        created.copy(avatarPath = pendingAvatarPath)
                    )
                }
            } else {
                TemplateRepository.update(
                    requireContext(),
                    cur.copy(
                        name = name,
                        avatar = avatarText,
                        avatarPath = pendingAvatarPath,
                        prompt = prompt
                    )
                )
            }
            Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }

        btnDelete.setOnClickListener {
            val cur = existing ?: return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除模板")
                .setMessage("会连同聊天记录一起删除，确定吗？")
                .setPositiveButton("删除") { _, _ ->
                    TemplateRepository.delete(requireContext(), cur)
                    parentFragmentManager.popBackStack()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        (activity as? TopBarHost)?.configureTopBar(
            title = if (existing == null) "新建模板" else "编辑模板",
            showLeft = true
        )

        ThemeEngine.tint(view, scheme)
    }
}