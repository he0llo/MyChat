package he.mychat.llo.net

data class ParsedReply(
    val sleepSeconds: Int,
    val dataMessages: List<String>,
    val save: String,
    val hasAllTags: Boolean
)

object AiParser {

    private val sleepTag = Regex("""wait\s*:""", RegexOption.IGNORE_CASE)
    private val dataTag = Regex("""data\s*:""", RegexOption.IGNORE_CASE)
    private val saveTag = Regex("""save\s*:""", RegexOption.IGNORE_CASE)

    private val sleepValueRe = Regex(
        """wait\s*:[ \t]*([^\n]*)""",
        RegexOption.IGNORE_CASE
    )

    private val dataAllRe = Regex(
        """data\s*:[ \t]*([\s\S]*?)(?=\n\s*(?:wait|data|save)\s*:|\z)""",
        RegexOption.IGNORE_CASE
    )

    private val saveAllRe = Regex(
        """save\s*:[ \t]*([\s\S]*?)(?=\n\s*(?:wait|data|save)\s*:|\z)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(raw: String): ParsedReply {
        // 1. 关键：把字面 "\n"（反斜杠+n）统一转成真实换行
        //    这样无论 AI 输出真实换行还是字面 \n，都能正确拆分
        var text = raw.trim().replace("\\n", "\n")

        // 2. 兼容标签黏在上一行末尾的情况：
        //    "躺床上呢 save:xxx" → "躺床上呢\nsave:xxx"
        text = text.replace(
            Regex("""([^\n])(wait|data|save)\s*:""", RegexOption.IGNORE_CASE)
        ) { match ->
            val prev = match.groupValues[1]
            val tag = match.groupValues[2].lowercase()
            "$prev\n$tag:"
        }
        text = text.trim()

        val hasSleep = sleepTag.containsMatchIn(text)
        val hasData = dataTag.containsMatchIn(text)
        val hasSave = saveTag.containsMatchIn(text)
        val hasAllTags = hasSleep && hasData && hasSave

        val sleepStr = sleepValueRe.find(text)
            ?.groupValues?.getOrNull(1)
            ?.trim()
            .orEmpty()
        val sleep = sleepStr.lineSequence()
            .firstOrNull()?.trim()
            ?.filter { it.isDigit() }
            ?.toIntOrNull() ?: 0

        val dataMessages = mutableListOf<String>()
        dataAllRe.findAll(text).forEach { m ->
            val block = m.groupValues.getOrNull(1).orEmpty().trim()
            if (block.isBlank()) return@forEach
            block.split("\n").forEach { piece ->
                val s = piece.trim()
                if (s.isNotEmpty()) dataMessages.add(s)
            }
        }

        val save = saveAllRe.findAll(text)
            .map { it.groupValues.getOrNull(1).orEmpty().trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

        return ParsedReply(
            sleepSeconds = sleep,
            dataMessages = dataMessages,
            save = save,
            hasAllTags = hasAllTags
        )
    }
}