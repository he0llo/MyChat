package he.mychat.llo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object StorageManager {

    private const val ROOT_NAME = "MyChat"
    private const val FILE_TEMPLATE = "template.json"
    private const val FILE_MEMORY = "memory.json"
    private const val FILE_NOTES = "notes.json"
    private const val FILE_SETTINGS = "settings.json"

    fun rootDir(context: Context): File {
        val external = File(Environment.getExternalStorageDirectory(), ROOT_NAME)
        return try {
            if (!external.exists()) external.mkdirs()
            if (external.exists() && external.canWrite()) external else fallback(context)
        } catch (e: Exception) {
            fallback(context)
        }
    }

    private fun fallback(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), ROOT_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun templateDir(context: Context, folder: String): File {
        val dir = File(rootDir(context), folder)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ---------- 设置 ----------

    fun readSettings(context: Context): AppSettings {
        return try {
            val f = File(rootDir(context), FILE_SETTINGS)
            if (f.exists()) AppSettings.fromJson(JSONObject(f.readText())) else AppSettings()
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun writeSettings(context: Context, settings: AppSettings) {
        try {
            File(rootDir(context), FILE_SETTINGS).writeText(settings.toJson())
        } catch (_: Exception) {
        }
    }

    // ---------- 模板 ----------

    fun readTemplate(context: Context, folder: String): ChatTemplate? {
        return try {
            val f = File(templateDir(context, folder), FILE_TEMPLATE)
            if (!f.exists()) null else ChatTemplate.fromJson(JSONObject(f.readText()), folder)
        } catch (e: Exception) {
            null
        }
    }

    fun writeTemplate(context: Context, template: ChatTemplate) {
        try {
            File(templateDir(context, template.folder), FILE_TEMPLATE)
                .writeText(template.toJson())
        } catch (_: Exception) {
        }
    }

    /**
     * 删除模板：连带模板文件夹和该模板的所有头像文件。
     */
    fun deleteTemplate(context: Context, template: ChatTemplate) {
        try {
            // 1. 删模板文件夹（聊天记录、记忆、template.json）
            templateDir(context, template.folder).deleteRecursively()

            // 2. 删头像目录里所有以该文件夹名为前缀的文件
            avatarsDir(context).listFiles()?.forEach { f ->
                val name = f.name
                if (name.startsWith("${template.folder}_")) {
                    f.delete()
                }
            }

            // 3. 若 template.avatarPath 指向的文件还在（理论上前缀已覆盖），再兜底删一次
            if (template.avatarPath.isNotBlank()) {
                val f = File(template.avatarPath)
                if (f.exists()) f.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun listTemplateFolders(context: Context): List<String> {
        val root = rootDir(context)
        return root.listFiles()
            ?.filter { it.isDirectory && File(it, FILE_TEMPLATE).exists() }
            ?.map { it.name }
            ?: emptyList()
    }

    fun uniqueFolder(context: Context, base: String): String {
        var name = base
        var i = 1
        while (File(rootDir(context), name).exists()) {
            name = "${base}_$i"
            i++
        }
        return name
    }

    // ---------- 记忆 ----------

    fun readMessages(context: Context, folder: String): MutableList<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        try {
            val f = File(templateDir(context, folder), FILE_MEMORY)
            if (!f.exists()) return list
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                list.add(ChatMessage.fromJson(arr.getJSONObject(i)))
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun writeMessages(context: Context, folder: String, messages: List<ChatMessage>) {
        try {
            val arr = JSONArray()
            messages.forEach { arr.put(JSONObject(it.toJson())) }
            File(templateDir(context, folder), FILE_MEMORY).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun clearMessages(context: Context, folder: String) {
        try {
            val f = File(templateDir(context, folder), FILE_MEMORY)
            if (f.exists()) f.writeText("[]")
        } catch (_: Exception) {
        }
    }

    fun readNotes(context: Context, folder: String): MutableList<String> {
        val list = mutableListOf<String>()
        try {
            val f = File(templateDir(context, folder), FILE_NOTES)
            if (!f.exists()) return list
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun appendNote(context: Context, folder: String, note: String) {
        if (note.isBlank()) return
        val list = readNotes(context, folder)
        list.add(note)
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            File(templateDir(context, folder), FILE_NOTES).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    // ---------- 头像 ----------

    fun avatarsDir(context: Context): File {
        val dir = File(rootDir(context), "avatars")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 把外部 Uri 指向的图片：
     *  1. 解码
     *  2. 中心裁剪为正方形
     *  3. 圆形裁剪
     *  4. 存为 PNG
     * 返回新文件的绝对路径。
     */
    fun importAvatar(context: Context, uri: Uri, prefix: String): String? {
        return try {
            val source = decodeBitmap(context, uri) ?: return null

            // 中心裁剪为正方形
            val side = minOf(source.width, source.height)
            val left = (source.width - side) / 2
            val top = (source.height - side) / 2
            val square = Bitmap.createBitmap(source, left, top, side, side)
            if (square !== source) source.recycle()

            // 圆形裁剪
            val size = 512.coerceAtMost(side)
            val scaled = Bitmap.createScaledBitmap(square, size, size, true)
            if (scaled !== square) square.recycle()

            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            canvas.drawARGB(0, 0, 0, 0)

            val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
            canvas.drawOval(rect, paint)

            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(scaled, 0f, 0f, paint)
            paint.xfermode = null

            scaled.recycle()

            val dir = avatarsDir(context)
            val name = "${prefix}_${System.currentTimeMillis()}.png"
            val target = File(dir, name)
            target.outputStream().use { out ->
                output.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            output.recycle()

            target.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 读取原图。若过大则按 2 的幂次采样，避免 OOM。
     */
    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            // 先读尺寸
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

            // 计算采样比例，目标长边约 1024
            var sample = 1
            var maxSide = maxOf(opts.outWidth, opts.outHeight)
            while (maxSide / sample > 1024) {
                sample *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
    }
    fun writeNotes(context: Context, folder: String, notes: List<String>) {
        try {
            val arr = JSONArray()
            notes.forEach { arr.put(it) }
            File(templateDir(context, folder), FILE_NOTES).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }
}