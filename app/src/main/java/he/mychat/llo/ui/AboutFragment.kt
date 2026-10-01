package he.mychat.llo.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import he.mychat.llo.R
import he.mychat.llo.TopBarHost
import he.mychat.llo.data.AboutInfo
import he.mychat.llo.ui.theme.ThemeEngine

class AboutFragment : Fragment(R.layout.fragment_about) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scheme = ThemeEngine.scheme(requireContext())
        view.setBackgroundColor(scheme.surface)

        (activity as? TopBarHost)?.configureTopBar(
            title = "关于软件",
            showLeft = true
        )

        val name = view.findViewById<TextView>(R.id.app_name)
        val version = view.findViewById<TextView>(R.id.app_version)
        val intro = view.findViewById<TextView>(R.id.txt_intro)
        val usage = view.findViewById<TextView>(R.id.txt_usage)
        val author = view.findViewById<TextView>(R.id.txt_author)
        val blockRepo = view.findViewById<View>(R.id.block_repo)
        val txtRepo = view.findViewById<TextView>(R.id.txt_repo)
        val iconRepo = view.findViewById<ImageView>(R.id.icon_repo)
        val rowRepo = view.findViewById<View>(R.id.row_repo)
        val license = view.findViewById<TextView>(R.id.txt_license)

        name.setTextColor(scheme.onSurface)
        version.setTextColor(scheme.onSurfaceVariant)
        intro.setTextColor(scheme.onSurface)
        usage.setTextColor(scheme.onSurface)
        author.setTextColor(scheme.onSurface)
        txtRepo.setTextColor(scheme.primary)
        license.setTextColor(scheme.onSurfaceVariant)

        version.text = "版本 ${getAppVersion()}"

        intro.text = AboutInfo.INTRO
        usage.text = AboutInfo.USAGE
        author.text = AboutInfo.AUTHOR
        license.text = AboutInfo.LICENSE

        if (AboutInfo.REPO_URL.isBlank()) {
            blockRepo.visibility = View.GONE
        } else {
            blockRepo.visibility = View.VISIBLE
            txtRepo.text = AboutInfo.REPO_URL
            iconRepo.imageTintList = ColorStateList.valueOf(scheme.primary)
            rowRepo.setOnClickListener {
                try {
                    val uri = AboutInfo.REPO_URL.toUri()
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "无法打开链接", Toast.LENGTH_SHORT).show()
                }
            }
        }

        ThemeEngine.tint(view, scheme)
    }

    private fun getAppVersion(): String {
        return try {
            val pm = requireContext().packageManager
            val info = pm.getPackageInfo(requireContext().packageName, 0)
            info.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }
}