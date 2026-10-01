package he.mychat.llo.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import he.mychat.llo.MainActivity
import he.mychat.llo.R
import he.mychat.llo.TopBarHost
import he.mychat.llo.data.TemplateRepository
import he.mychat.llo.ui.theme.ThemeEngine

class FriendInfoFragment : Fragment(R.layout.fragment_friend_info) {

    companion object {
        const val RESULT_CHAT_CLEARED = "chat_cleared"
        private const val ARG_FOLDER = "folder"

        fun newInstance(folder: String): FriendInfoFragment {
            val f = FriendInfoFragment()
            f.arguments = Bundle().apply { putString(ARG_FOLDER, folder) }
            return f
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scheme = ThemeEngine.scheme(requireContext())
        view.setBackgroundColor(scheme.surface)

        val folder = requireArguments().getString(ARG_FOLDER) ?: return
        val t = TemplateRepository.find(requireContext(), folder) ?: run {
            Toast.makeText(requireContext(), "模板不存在", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        val avatar = view.findViewById<AvatarView>(R.id.avatar)
        val name = view.findViewById<TextView>(R.id.name)
        val subtitle = view.findViewById<TextView>(R.id.subtitle)
        val btnClear = view.findViewById<MaterialButton>(R.id.btn_clear)
        val btnMemory = view.findViewById<MaterialButton>(R.id.btn_memory)
        val btnDeleteFriend = view.findViewById<MaterialButton>(R.id.btn_delete_friend)

        avatar.bind(
            t.avatar.ifBlank { t.name },
            t.avatarPath.ifBlank { null },
            scheme.primaryContainer,
            scheme.onPrimaryContainer
        )
        name.text = t.name
        name.setTextColor(scheme.onSurface)

        subtitle.text = if (t.enabled) "聊天对象" else "聊天对象（已停用）"
        subtitle.setTextColor(scheme.onSurfaceVariant)

        if (t.enabled) {
            btnDeleteFriend.text = "停用好友"
            btnDeleteFriend.setTextColor(0xFFB3261E.toInt())
        } else {
            btnDeleteFriend.text = "启用好友"
            btnDeleteFriend.setTextColor(scheme.primary)
        }

        btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除聊天记录")
                .setMessage("与「${t.name}」的所有聊天记录将被清空，不可恢复。")
                .setPositiveButton("删除") { _, _ ->
                    TemplateRepository.clearMessages(requireContext(), folder)
                    parentFragmentManager.setFragmentResult(
                        RESULT_CHAT_CLEARED,
                        Bundle().apply { putString("folder", folder) }
                    )
                    Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        btnMemory.setOnClickListener {
            (activity as? MainActivity)?.navigate(
                MemoryManageFragment.newInstance(folder)
            )
        }

        btnDeleteFriend.setOnClickListener {
            val current = TemplateRepository.find(requireContext(), folder) ?: return@setOnClickListener
            if (current.enabled) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("停用好友")
                    .setMessage("停用后「${current.name}」不会在聊天列表显示，但聊天记录和记忆会保留。")
                    .setPositiveButton("停用") { _, _ ->
                        TemplateRepository.update(
                            requireContext(),
                            current.copy(enabled = false)
                        )
                        Toast.makeText(requireContext(), "已停用好友", Toast.LENGTH_SHORT).show()
                        (activity as? MainActivity)?.resetToHome()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                TemplateRepository.update(
                    requireContext(),
                    current.copy(enabled = true)
                )
                Toast.makeText(requireContext(), "已启用好友", Toast.LENGTH_SHORT).show()
                (activity as? MainActivity)?.resetToHome()
            }
        }

        (activity as? TopBarHost)?.configureTopBar(
            title = "对方信息",
            showLeft = true
        )

        ThemeEngine.tint(view, scheme)
    }
}