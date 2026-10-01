package he.mychat.llo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import he.mychat.llo.R
import he.mychat.llo.TopBarHost
import he.mychat.llo.data.SettingsRepository
import he.mychat.llo.net.AiClient
import he.mychat.llo.ui.theme.ThemeEngine
import kotlinx.coroutines.launch

class ApiSettingsFragment : Fragment(R.layout.fragment_api_settings) {

    private lateinit var inputApi: EditText
    private lateinit var inputKey: EditText
    private lateinit var inputModel: EditText
    private lateinit var dropdown: AutoCompleteTextView
    private lateinit var switchThinking: MaterialSwitch
    private lateinit var txtTestResult: TextView

    private val modelList = mutableListOf<String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scheme = ThemeEngine.scheme(requireContext())
        view.setBackgroundColor(scheme.surface)

        inputApi = view.findViewById(R.id.input_api)
        inputKey = view.findViewById(R.id.input_key)
        inputModel = view.findViewById(R.id.input_model)
        dropdown = view.findViewById(R.id.dropdown_model)
        switchThinking = view.findViewById(R.id.switch_thinking)
        txtTestResult = view.findViewById(R.id.txt_test_result)

        (activity as? TopBarHost)?.configureTopBar(
            title = "API 设置",
            showLeft = true
        )

        val settings = SettingsRepository.get(requireContext())
        inputApi.setText(settings.apiBaseUrl)
        inputKey.setText(settings.apiKey)
        inputModel.setText(settings.model)
        switchThinking.isChecked = settings.enableThinking

        // 模型下拉：切换时同步到手动输入框
        dropdown.setOnItemClickListener { _, _, position, _ ->
            val picked = modelList.getOrNull(position) ?: return@setOnItemClickListener
            inputModel.setText(picked)
        }

        view.findViewById<MaterialButton>(R.id.btn_load_models)
            .setOnClickListener { loadModels() }

        view.findViewById<MaterialButton>(R.id.btn_test)
            .setOnClickListener { testModel() }

        view.findViewById<MaterialButton>(R.id.btn_save)
            .setOnClickListener { save() }

        ThemeEngine.tint(view, scheme)
    }

    private fun loadModels() {
        val base = inputApi.text.toString().trim()
        val key = inputKey.text.toString().trim()
        if (base.isBlank()) {
            Toast.makeText(requireContext(), "请先填写 API 地址", Toast.LENGTH_SHORT).show()
            return
        }
        if (key.isBlank()) {
            Toast.makeText(requireContext(), "请先填写 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val loading = MaterialAlertDialogBuilder(requireContext())
            .setMessage("正在获取模型列表…")
            .setCancelable(false)
            .create()
        loading.show()

        lifecycleScope.launch {
            try {
                val list = AiClient.listModels(base, key)
                loading.dismiss()
                if (list.isEmpty()) {
                    Toast.makeText(requireContext(), "未获取到模型", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                modelList.clear()
                modelList.addAll(list)
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_list_item_1,
                    modelList
                )
                dropdown.setAdapter(adapter)
                Toast.makeText(
                    requireContext(),
                    "共 ${list.size} 个模型",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                loading.dismiss()
                Toast.makeText(
                    requireContext(),
                    "获取失败：${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun testModel() {
        val base = inputApi.text.toString().trim()
        val key = inputKey.text.toString().trim()
        val model = currentModel()
        if (base.isBlank() || key.isBlank() || model.isBlank()) {
            Toast.makeText(requireContext(), "请先填写 API 地址、Key 和模型", Toast.LENGTH_SHORT).show()
            return
        }

        val scheme = ThemeEngine.scheme(requireContext())
        txtTestResult.visibility = View.VISIBLE
        txtTestResult.setTextColor(scheme.onSurfaceVariant)
        txtTestResult.text = "测试中…"

        lifecycleScope.launch {
            try {
                val (cost, reply) = AiClient.testModel(
                    base, key, model, switchThinking.isChecked
                )
                txtTestResult.text = "耗时：${cost} ms\n模型回复：$reply"
            } catch (e: Exception) {
                txtTestResult.text = "失败：${e.message}"
            }
        }
    }

    private fun currentModel(): String {
        val manual = inputModel.text.toString().trim()
        return manual.ifBlank { dropdown.text?.toString()?.trim().orEmpty() }
    }

    private fun save() {
        val settings = SettingsRepository.get(requireContext())
        settings.apiBaseUrl = inputApi.text.toString().trim()
        settings.apiKey = inputKey.text.toString().trim()
        settings.model = currentModel()
        settings.enableThinking = switchThinking.isChecked
        SettingsRepository.save(requireContext(), settings)
        Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
    }
}