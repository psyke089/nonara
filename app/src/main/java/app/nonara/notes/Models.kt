package app.nonara.notes

import android.content.Context
import org.json.JSONArray
import java.io.File

class Models(private val context: Context) {
    private val catalog by lazy { JSONArray(context.assets.open("models.json").bufferedReader().use { it.readText() }) }
    fun ids(): List<String> { val entries=catalog; return (0 until entries.length()).map { entries.getJSONObject(it).getString("id") } }
    fun definition(size: String) = (0 until catalog.length()).map { catalog.getJSONObject(it) }.first { it.getString("id") == size }
    fun info(id: String): SpeechModel {
        val entry=definition(id)
        val languages=entry.getJSONArray("languages")
        val files=entry.getJSONArray("files")
        return SpeechModel(id,entry.getString("name"),entry.getString("description"),
            SpeechModelFamily.valueOf(entry.getString("family").uppercase(java.util.Locale.ROOT)),
            (0 until languages.length()).map { languages.getString(it) },
            (0 until files.length()).associate { files.getJSONObject(it).let { it.getString("role") to it.getString("name") } })
    }
    fun supports(id: String, language: String) = info(id).supports(language)
    fun directory(size: String) = File(context.filesDir,"models/$size-${definition(size).getString("version")}")
    fun ready(size: String) = File(directory(size),"complete").exists()
    fun bytes(size: String): Long { val files=definition(size).getJSONArray("files"); return (0 until files.length()).sumOf { files.getJSONObject(it).getLong("size") } }
    val root get() = File(context.filesDir,"models")
    fun transferSpec(id: String): TransferSpec {
        val entry=definition(id)
        val files=entry.getJSONArray("files")
        return TransferSpec(id,entry.getString("version"),(0 until files.length()).map {
            val file=files.getJSONObject(it)
            TransferFile(file.getString("name"),file.getString("url"),file.getLong("size"),file.getString("sha256"))
        })
    }
    fun delete(size: String) { ModelTransfer(root).delete(transferSpec(size)) }
    companion object { fun hash(file: File): String = ModelTransfer.hash(file) }
}
