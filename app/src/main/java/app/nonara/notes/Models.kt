package app.nonara.notes

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class Models(private val context: Context) {
    private val catalog get() = JSONArray(context.assets.open("models.json").bufferedReader().use { it.readText() })
    fun ids(): List<String> { val entries=catalog; return (0 until entries.length()).map { entries.getJSONObject(it).getString("id") } }
    fun definition(size: String) = (0 until catalog.length()).map { catalog.getJSONObject(it) }.first { it.getString("id") == size }
    fun directory(size: String) = File(context.filesDir,"models/$size-${definition(size).getString("version")}")
    fun ready(size: String) = File(directory(size),"complete").exists()
    fun bytes(size: String): Long { val files=definition(size).getJSONArray("files"); return (0 until files.length()).sumOf { files.getJSONObject(it).getLong("size") } }
    suspend fun install(size: String, progress: (String)->Unit) = withContext(Dispatchers.IO) {
        val dest=directory(size); val stage=File(dest.parentFile,dest.name+".partial").apply { mkdirs() }
        require(stage.usableSpace > bytes(size) + 32*1024*1024) { "Not enough free storage for this model" }
        val files=definition(size).getJSONArray("files")
        for(i in 0 until files.length()) {
            val spec=files.getJSONObject(i); val file=File(stage,spec.getString("name")); val expected=spec.getLong("size")
            val digest=spec.getString("sha256")
            if(file.exists() && file.length()==expected && hash(file)==digest) continue
            file.delete()
            val connection=URL(spec.getString("url")).openConnection() as HttpURLConnection
            connection.connectTimeout=30000; connection.readTimeout=30000
            try {
                require(connection.responseCode==200) { "Download failed (${connection.responseCode})" }
                connection.inputStream.use { input -> FileOutputStream(file).use { output ->
                    val buffer=ByteArray(65536); var count=0L
                    while(true) { coroutineContext.ensureActive(); val n=input.read(buffer); if(n<0) break
                        count+=n; require(count<=expected) { "Unexpected download size" }; output.write(buffer,0,n)
                        progress("${spec.getString("name")}: ${count*100/expected}%")
                    }; output.fd.sync()
                } }
                require(file.length()==expected && hash(file)==digest) { "Model checksum mismatch" }
            } finally { connection.disconnect() }
        }
        atomicWrite(File(stage,"complete"),definition(size).toString().toByteArray())
        require(!dest.exists()) { "Model is already installed" }
        require(stage.renameTo(dest)) { "Unable to install model" }
    }
    fun delete(size: String) { directory(size).deleteRecursively() }
    companion object { fun hash(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer=ByteArray(65536); while(true) { val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    } }
}
