package he.mychat.llo.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import he.mychat.llo.MainActivity
import he.mychat.llo.R
import he.mychat.llo.TopBarHost
import he.mychat.llo.data.TemplateRepository
import he.mychat.llo.net.AiParser
import he.mychat.llo.net.ChatSender
import he.mychat.llo.ui.theme.ThemeEngine
import kotlinx.coroutines.launch

class ChatListFragment : Fragment(R.layout.fragment_chat_list) {

    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var adapter: ChatListAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scheme = ThemeEngine.scheme(requireContext())
        view.setBackgroundColor(scheme.surface)

        recycler = view.findViewById(R.id.recycler)
        empty = view.findViewById(R.id.empty)
        empty.setTextColor(scheme.onSurfaceVariant)

        adapter = ChatListAdapter(scheme) { template ->
            if (!template.enabled) {
                Toast.makeText(
                    requireContext(),
                    "「${template.name}」已停用，请在模板配置里开启",
                    Toast.LENGTH_SHORT
                ).show()
                return@ChatListAdapter
            }
            (activity as? MainActivity)?.navigate(ChatFragment.newInstance(template.folder))
        }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        (activity as? TopBarHost)?.configureTopBar(
            title = "MyChat",
            showLeft = false,
            showRight = true,
            rightIcon = R.drawable.ic_settings,
            onRight = {
                (activity as? MainActivity)?.navigate(SettingsFragment())
            }
        )

        viewLifecycleOwner.lifecycleScope.launch {
            ChatSender.changes.collect { reload() }
        }

        ThemeEngine.tint(view, scheme)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val ctx = requireContext()
        val templates = TemplateRepository.list(ctx).filter { it.enabled }

        val previews = mutableMapOf<String, String>()
        val unreads = mutableMapOf<String, Int>()

        templates.forEach { t ->
            ChatSender.loadMessages(ctx, t.folder)
            previews[t.folder] = buildPreview(t.folder)
            unreads[t.folder] = ChatSender.getUnread(t.folder)
        }

        adapter.submit(templates, previews, unreads)
        empty.visibility = if (templates.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildPreview(folder: String): String {
        val list = ChatSender.loadMessages(requireContext(), folder)
        val last = list.lastOrNull() ?: return "还没有聊天记录"

        val raw = when (last.role) {
            "assistant" -> {
                // 取最后一条气泡：data 里最后一个；没 data 时用原文
                val parsed = AiParser.parse(last.content)
                parsed.dataMessages.lastOrNull() ?: last.content
            }
            "user" -> extractUserText(last.content)
            else -> last.content
        }

        return raw.replace("\n", " ").take(60)
    }

    private fun extractUserText(content: String): String {
        val idx = content.indexOf("data:")
        if (idx < 0) return content
        return content.substring(idx + 5).trimStart()
    }
}