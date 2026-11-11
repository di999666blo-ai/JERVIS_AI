package ai.diablo.bobby

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BobbyMemory(
    val id: String,
    val category: String,
    val title: String,
    val content: String,
    val updatedAt: Long
)

/** Local, user-controlled memory. Secrets are rejected/redacted and never used for credentials. */
class BobbyMemoryStore(context: Context) {\n    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences("bobby_user_memory", Context.MODE_PRIVATE)
    private val lock = Any()
    private val backupExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var backupStatusValue = prefs.getString("drive_backup_status", "PHONE ONLY") ?: "PHONE ONLY"
    val driveBackupStatus: String get() = backupStatusValue

    /** Connects a user-selected Google Drive/SAF folder and creates BOBBY Memory inside it. */
    fun connectDriveBackup(treeUri: Uri, grantFlags: Int, callback: (Result<String>) -> Unit) {
        backupExecutor.execute {
            val result = runCatching {
                val resolver = contextResolver()
                val takeFlags = grantFlags and (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                resolver.takePersistableUriPermission(treeUri, takeFlags)
                val treeId = DocumentsContract.getTreeDocumentId(treeUri)
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
                var folderUri: Uri? = null
                resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        if (nameCol >= 0 && cursor.getString(nameCol) == "BOBBY Memory" && idCol >= 0) {
                            folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idCol))
                            break
                        }
                    }
                }
                if (folderUri == null) {
                    val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
                    folderUri = DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, "BOBBY Memory")
                }
                val target = requireNotNull(folderUri) { "Google Drive did not create the BOBBY Memory folder." }
                prefs.edit().putString("drive_tree_uri", treeUri.toString()).putString("drive_folder_uri", target.toString()).apply()
                backupStatusValue = "CONNECTED • syncing"
                prefs.edit().putString("drive_backup_status", backupStatusValue).apply()
                syncDriveBackupBlocking()
                "Google Drive connected. Your memories will back up automatically."
            }
            callback(result)
        }
    }

    fun disconnectDriveBackup() {
        prefs.edit().remove("drive_tree_uri").remove("drive_folder_uri").putString("drive_backup_status", "PHONE ONLY").apply()
        backupStatusValue = "PHONE ONLY"
    }

    fun syncDriveBackup(callback: ((Result<String>) -> Unit)? = null) {
        backupExecutor.execute {
            val result = runCatching { syncDriveBackupBlocking(); "Memory backup synchronised." }
            callback?.invoke(result)
        }
    }

    private fun contextResolver() = appContext.contentResolver
    private val appContext get() = context.applicationContext

    fun save(category: String, title: String, content: String): BobbyMemory {
        require(title.isNotBlank() && content.isNotBlank()) { "Title and content are required." }
        val safe = redactSecrets(content.trim()).take(12000)
        require(safe.isNotBlank()) { "Memory contains no safe content after secret filtering." }
        val item = BobbyMemory(UUID.randomUUID().toString(), category.trim().ifBlank { "General" }.take(60),
            title.trim().take(160), safe, System.currentTimeMillis())
        synchronized(lock) {
            val all = readAll().filterNot { it.title.equals(item.title, true) && it.category.equals(item.category, true) }.toMutableList()
            all.add(0, item)
            writeAll(all.take(500))
        }
        return item
    }

    fun list(): List<BobbyMemory> = synchronized(lock) { readAll().sortedByDescending { it.updatedAt } }

    /** Updates an existing memory while preserving its stable ID. */
    fun update(id: String, category: String, title: String, content: String): BobbyMemory {
        require(title.isNotBlank() && content.isNotBlank()) { "Title and content are required." }
        val safe = redactSecrets(content.trim()).take(12000)
        require(safe.isNotBlank()) { "Memory contains no safe content after secret filtering." }
        synchronized(lock) {
            val old = readAll().firstOrNull { it.id == id } ?: error("Memory no longer exists.")
            val updated = old.copy(category = category.trim().ifBlank { "General" }.take(60),
                title = title.trim().take(160), content = safe, updatedAt = System.currentTimeMillis())
            val all = readAll().filterNot { it.id == id || (it.title.equals(updated.title, true) && it.category.equals(updated.category, true)) }
            writeAll((listOf(updated) + all).take(500))
            return updated
        }
    }

    /** Explicit natural-language memory command. Returns null when the message is not a save request. */
    fun rememberFromMessage(message: String): BobbyMemory? {
        val match = Regex("(?is)^\\s*(?:bobby[, ]+)?(?:please\\s+)?remember\\s+(?:this|that|the following)?\\s*[:,-]?\\s*(.+?)\\s*$")
            .matchEntire(message) ?: return null
        val raw = match.groupValues[1].trim().trim('"', ' ')
        if (raw.length < 3 || raw.equals("this", true) || raw.equals("that", true)) return null
        val category = when {
            Regex("(?i)\\b(project|app|repository|github|build|code|feature)\\b").containsMatchIn(raw) -> "Projects"
            Regex("(?i)\\b(birthday|anniversary|appointment|meeting|date|remind)\\b").containsMatchIn(raw) -> "Important Dates"
            Regex("(?i)\\b(always|prefer|preference|instruction|please use|don't|do not)\\b").containsMatchIn(raw) -> "Instructions"
            Regex("(?i)\\b(my name|i am|i'm|my favourite|my favorite|i like|i prefer)\\b").containsMatchIn(raw) -> "Personal"
            else -> "General"
        }
        val title = raw.replace(Regex("\\s+"), " ").take(72).let { if (it.length == 72) it.take(69) + "..." else it }
        return save(category, title, raw)
    }
    fun search(query: String): List<BobbyMemory> {
        val terms = query.trim().split(Regex("\\s+")).filter { it.length >= 3 }.distinct()
        if (terms.isEmpty()) return list()
        return list().filter { item -> terms.any { term ->
            item.title.contains(term, true) || item.content.contains(term, true) || item.category.contains(term, true)
        } }
    }
    fun delete(id: String) = synchronized(lock) { writeAll(readAll().filterNot { it.id == id }) }
    fun clear() = synchronized(lock) { writeAll(emptyList()) }
    fun exportJson(): String = synchronized(lock) { JSONArray().also { a -> readAll().forEach { a.put(toJson(it)) } }.toString(2) }


    private fun syncDriveBackupBlocking() {
        val folderText = prefs.getString("drive_folder_uri", null) ?: return
        val treeText = prefs.getString("drive_tree_uri", null) ?: return
        val folderUri = Uri.parse(folderText)
        val treeUri = Uri.parse(treeText)
        val resolver = contextResolver()
        val folderId = DocumentsContract.getDocumentId(folderUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        var backupUri: Uri? = null
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (nameCol >= 0 && cursor.getString(nameCol) == "bobby-memory-backup.json" && idCol >= 0) {
                    backupUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idCol))
                    break
                }
            }
        }
        if (backupUri == null) {
            backupUri = DocumentsContract.createDocument(resolver, folderUri, "application/json", "bobby-memory-backup.json")
        }
        val target = requireNotNull(backupUri) { "Drive provider could not create the memory backup file." }
        resolver.openOutputStream(target, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(exportJson()) }
            ?: error("Could not write the memory backup to Drive.")
        backupStatusValue = "CONNECTED • backed up"
        prefs.edit().putString("drive_backup_status", backupStatusValue).apply()
    }
\n    private fun readAll(): List<BobbyMemory> = runCatching {
        val a = JSONArray(prefs.getString(KEY, "[]"))
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let {
            BobbyMemory(it.optString("id"), it.optString("category"), it.optString("title"),
                it.optString("content"), it.optLong("updatedAt"))
        } }
    }.getOrDefault(emptyList())

    private fun writeAll(items: List<BobbyMemory>) {
        val a = JSONArray()
        items.forEach { a.put(toJson(it)) }
        prefs.edit().putString(KEY, a.toString()).apply()
        if (!prefs.getString("drive_folder_uri", null).isNullOrBlank()) {
            backupExecutor.execute { runCatching { syncDriveBackupBlocking() } }
        }
    }
    private fun toJson(m: BobbyMemory) = JSONObject().put("id", m.id).put("category", m.category)
        .put("title", m.title).put("content", m.content).put("updatedAt", m.updatedAt)

    private fun redactSecrets(value: String): String {
        var s = value
        val patterns = listOf(
            Regex("(?i)(api[_ -]?key|access[_ -]?token|password|secret|private[_ -]?key)\\s*[:=]\\s*[^\\s,;]+"),
            Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]{12,}"),
            Regex("\\b[A-Za-z0-9_-]{32,}\\b")
        )
        patterns.forEach { s = it.replace(s, "[REDACTED]") }
        return s
    }
    companion object { private const val KEY = "memories_v1" }
}
