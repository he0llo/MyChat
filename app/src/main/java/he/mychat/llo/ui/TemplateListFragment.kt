package he.mychat.llo.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import he.mychat.llo.MainActivity
import he.mychat.llo.R
import he.mychat.llo.TopBarHost
import he.mychat.llo.data.TemplateRepository
import he.mychat.llo.ui.theme.ThemeEngine

class TemplateListFragment : Fragment(R.layout.fragment_template_list) {

    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var adapter: TemplateAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scheme = ThemeEngine.scheme(requireContext())
        view.setBackgroundColor(scheme.surface)

        recycler = view.findViewById(R.id.recycler)
        empty = view.findViewById(R.id.empty)
        empty.setTextColor(scheme.onSurfaceVariant)

        adapter = TemplateAdapter(
            scheme,
            onClick = { t ->
                (activity as? MainActivity)?.navigate(
                    TemplateEditFragment.newInstance(t.folder)
                )
            },
            onToggle = { t, enabled ->
                TemplateRepository.update(
                    requireContext(),
                    t.copy(enabled = enabled)
                )
                // 立即刷新本地列表，避免变灰需要重启界面才生效
                reload()
            }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val fab = view.findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            (activity as? MainActivity)?.navigate(TemplateEditFragment.newInstance(null))
        }

        (activity as? TopBarHost)?.configureTopBar(
            title = "模板配置",
            showLeft = true
        )

        ThemeEngine.tint(view, scheme)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val list = TemplateRepository.list(requireContext())
        adapter.submit(list)
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }
}