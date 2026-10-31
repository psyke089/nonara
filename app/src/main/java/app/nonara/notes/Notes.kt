package app.nonara.notes

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import java.util.UUID
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.constructor.SafeConstructor

fun newId(): String {
    val random = UUID.randomUUID()
    return UUID((System.currentTimeMillis() shl 16) or 0x7000L or (random.mostSignificantBits and 0xfff),
        (random.leastSignificantBits and 0x3fffffffffffffffL) or Long.MIN_VALUE).toString()
}
private val timestamp = DateTimeFormatterBuilder().appendInstant(3).toFormatter()
data class Note(val id: String, val title: String?, val body: String, val createdAt: Instant,
    val updatedAt: Instant, val createdVia: String, val extra: Map<String, Any?> = emptyMap()) {
    val displayTitle: String get() = title ?: body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.removePrefix("# ") ?: "Untitled thought"
    companion object {
        fun create(body: String = "", source: String = "text", id: String = newId()): Note {
            val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
            return Note(id, null, body, now, now, source)
        }
    }
}
object NoteCodec {
    private val keys = setOf("schema_version", "id", "title", "created_at", "updated_at", "created_via")
    private fun yaml() = Yaml(SafeConstructor(LoaderOptions().apply { isAllowDuplicateKeys = false; maxAliasesForCollections = 0; codePointLimit = 16384 }))
    fun encode(n: Note): String {
        val fields = linkedMapOf<String, Any?>("schema_version" to 1, "id" to n.id, "title" to n.title,
            "created_at" to timestamp.format(n.createdAt), "updated_at" to timestamp.format(n.updatedAt), "created_via" to n.createdVia)
        fields.putAll(n.extra.filterKeys { it !in keys })
        val options = DumperOptions().apply { defaultFlowStyle = DumperOptions.FlowStyle.BLOCK; defaultScalarStyle = DumperOptions.ScalarStyle.DOUBLE_QUOTED }
        return "---\n" + fields.entries.joinToString("\n") { (key, value) -> key + ": " + when(value) { null -> "null"; is Number -> value.toString(); else -> Yaml(options).dump(value.toString()).trimEnd() } } + "\n---\n\n" + n.body
    }
    fun decode(raw: String): Note {
        try {
            val text = raw.removePrefix("\uFEFF")
            require(text.startsWith("---\n") || text.startsWith("---\r\n")) { "Missing front matter" }
            val firstEnd = text.indexOf('\n') + 1
            val end = Regex("(?m)^---\\r?$").find(text, firstEnd) ?: error("Missing closing delimiter")
            val header = text.substring(firstEnd, end.range.first)
            require(header.toByteArray().size + 8 <= 16384) { "Front matter too large" }
            yaml().parse(java.io.StringReader(header)).forEach { event ->
                require(event !is org.yaml.snakeyaml.events.AliasEvent) { "Aliases unsupported" }
                if(event is org.yaml.snakeyaml.events.NodeEvent) require(event.anchor == null) { "Anchors unsupported" }
                if(event is org.yaml.snakeyaml.events.ScalarEvent) require(event.tag == null) { "Explicit tags unsupported" }
            }
            val map = yaml().load<Map<String, Any?>>(header) ?: error("Missing metadata")
            require(map.values.all { it == null || it is String || it is Int || it is Long }) { "Only scalar metadata is supported" }
            require(map.keys.containsAll(keys) && map["schema_version"] == 1) { "Unsupported note schema" }
            val id = map["id"] as String
            require(UUID.fromString(id).toString() == id) { "Invalid ID" }
            val title = map["title"]
            require(title == null || title is String)
            val created = Instant.parse(map["created_at"] as String)
            val updated = Instant.parse(map["updated_at"] as String)
            require(updated >= created)
            val source = map["created_via"] as String
            require(source in listOf("text", "voice"))
            val closingEnd = text.indexOf('\n',end.range.first)
            var body = if(closingEnd < 0) "" else text.substring(closingEnd + 1)
            body = if (body.startsWith("\r\n")) body.substring(2) else body.removePrefix("\n")
            return Note(id, title as String?, body, created, updated, source, map.filterKeys { it !in keys })
        } catch (e: Exception) { throw IllegalArgumentException("Invalid note: ${e.message}", e) }
    }
}
fun atomicWrite(file: File, bytes: ByteArray) {
    file.parentFile!!.mkdirs()
    val tmp = File(file.parentFile, file.name + ".writing")
    FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
class NoteStore(private val root: File) {
    val problems = mutableListOf<String>()
    private val notes = File(root, "notes").apply { mkdirs() }
    private val trashDir = File(root, "trash").apply { mkdirs() }
    private val recovery = File(root, "recovery").apply { mkdirs() }
    private val revisions = mutableMapOf<String, Long>()
    init {
        recovery.listFiles()?.filter { it.extension == "md" }?.forEach { f ->
            runCatching { val n = read(f); atomicWrite(path(notes,n.id), f.readBytes()); f.delete() }.onFailure { problems += f.name }
        }
    }
    private fun path(dir: File, id: String): File { require(UUID.fromString(id).toString() == id); return File(dir,"$id.md") }
    private fun read(f: File): Note {
        require(f.length() <= 4 * 1024 * 1024) { "Note exceeds 4 MiB" }
        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(f.readBytes())).toString()
        return NoteCodec.decode(text).also { require(it.id == f.nameWithoutExtension) { "Filename mismatch" } }
    }
    @Synchronized fun list(deleted: Boolean = false): List<Note> {
        problems.clear()
        return (if(deleted) trashDir else notes).listFiles().orEmpty().filter { it.extension == "md" }.mapNotNull {
            runCatching { read(it) }.onFailure { e -> problems += "${it.name}: ${e.message}" }.getOrNull()
        }.sortedWith(compareByDescending<Note> { it.updatedAt }.thenBy { it.id })
    }
    @Synchronized fun get(id: String): Note? = path(notes,id).takeIf { it.exists() }?.let { read(it) }
    @Synchronized fun journal(n: Note, revision: Long, expectedBody: String? = null) {
        if (revision < (revisions[n.id] ?: Long.MIN_VALUE)) return
        if (expectedBody != null) check(get(n.id)?.body.orEmpty() == expectedBody) { "This note changed while editing. Your text is still in the editor; copy it before reopening." }
        atomicWrite(path(recovery,n.id),NoteCodec.encode(n).toByteArray())
        revisions[n.id]=revision
    }
    @Synchronized fun saveEdited(n: Note, revision: Long, expectedBody: String) {
        check(get(n.id)?.body.orEmpty() == expectedBody) { "This note changed while editing. Copy your draft before reopening." }
        save(n,revision)
    }
    @Synchronized fun save(n: Note, revision: Long = System.nanoTime()) {
        if (revision < (revisions[n.id] ?: Long.MIN_VALUE)) return
        val bytes = NoteCodec.encode(n).toByteArray()
        atomicWrite(path(recovery,n.id),bytes)
        atomicWrite(path(notes,n.id),bytes)
        path(recovery,n.id).delete()
        revisions[n.id] = revision
    }
    @Synchronized fun trash(id: String) {
        val file = path(notes,id)
        require(!path(trashDir,id).exists()) { "Already in trash" }
        atomicWrite(File(trashDir,"$id.deleted"), Instant.now().toString().toByteArray())
        Files.move(file.toPath(),path(trashDir,id).toPath(),StandardCopyOption.ATOMIC_MOVE)
    }
    @Synchronized fun restore(id: String) {
        require(!path(notes,id).exists()) { "Note already exists" }
        Files.move(path(trashDir,id).toPath(),path(notes,id).toPath(),StandardCopyOption.ATOMIC_MOVE)
        File(trashDir,"$id.deleted").delete()
    }
    @Synchronized fun purge() {
        trashDir.listFiles()?.filter { it.extension == "deleted" }?.forEach {
            val time = runCatching { Instant.parse(it.readText()) }.getOrNull()
            if(time != null && time < Instant.now().minusSeconds(30L*86400)) { path(trashDir,it.nameWithoutExtension).delete(); it.delete() }
        }
    }
    @Synchronized fun appendCapture(target: String, capture: String, text: String, original: String?, caret: Int?, create: Boolean = false): Note {
        val draft=path(recovery,target)
        if(draft.exists()) { val recovered=read(draft); save(recovered,revisions[target] ?: 0) }
        val old = get(target) ?: if(create) Note.create(source="voice",id=target) else error("Target note no longer exists")
        if(old.extra["last_applied_capture_id"] == capture) return old
        require(text.isNotBlank()) { "No speech recognized" }
        val body = if(original == old.body && caret != null) {
            val at = caret.coerceIn(0,old.body.length)
            val left = old.body.take(at); val right = old.body.drop(at)
            left + (if(left.lastOrNull()?.isLetterOrDigit()==true && text.first().isLetterOrDigit()) " " else "") + text +
                (if(text.last().isLetterOrDigit() && right.firstOrNull()?.isLetterOrDigit()==true) " " else "") + right
        } else old.body + (if(old.body.isBlank()) "" else "\n\n") + text
        return old.copy(body=body,updatedAt=maxOf(Instant.now(),old.updatedAt),extra=old.extra + ("last_applied_capture_id" to capture)).also { save(it,revisions[target] ?: 0) }
    }
    @Synchronized fun importNote(raw: ByteArray, name: String) {
        val n = NoteCodec.decode(Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw)).toString())
        require(name == "${n.id}.md") { "Filename mismatch" }
        require(!path(notes,n.id).exists() && !path(trashDir,n.id).exists()) { "Conflicting note ID" }
        save(n.copy(extra=n.extra - "last_applied_capture_id"))
    }
}
