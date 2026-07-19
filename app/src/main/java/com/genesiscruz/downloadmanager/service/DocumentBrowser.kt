package com.genesiscruz.downloadmanager.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** One file or folder inside a user-picked SAF tree. */
data class BrowserEntry(
    val documentId: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val dateModifiedMillis: Long,
    val mimeType: String?,
    val uri: Uri
)

/** One level of the navigation breadcrumb: a folder's document id + display name. */
data class BrowserFolder(val documentId: String, val name: String)

/**
 * Browses an arbitrary folder tree the user picked via
 * [Intent.ACTION_OPEN_DOCUMENT_TREE] — the only sanctioned way to read
 * folders outside Downloads/app-private storage under scoped storage.
 * The picked tree's permission is persisted so it survives app restarts.
 */
@Singleton
class DocumentBrowser @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("folder_browser", Context.MODE_PRIVATE)

    fun savedTreeUri(): Uri? = prefs.getString(KEY_TREE_URI, null)?.let { Uri.parse(it) }

    fun rememberTreeUri(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    fun forgetTreeUri() {
        prefs.edit().remove(KEY_TREE_URI).apply()
    }

    fun rootFolder(treeUri: Uri): BrowserFolder =
        BrowserFolder(DocumentsContract.getTreeDocumentId(treeUri), treeUri.displayRootName())

    private fun Uri.displayRootName(): String =
        lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "Folder"

    fun list(treeUri: Uri, parentDocumentId: String): List<BrowserEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val result = mutableListOf<BrowserEntry>()
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol)
                    val mime = cursor.getString(mimeCol)
                    result += BrowserEntry(
                        documentId = docId,
                        name = cursor.getString(nameCol) ?: docId,
                        isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        sizeBytes = cursor.getLong(sizeCol),
                        dateModifiedMillis = cursor.getLong(dateCol),
                        mimeType = mime,
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    )
                }
            }
        }
        return result.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /** @return true if the entry was deleted. */
    fun delete(entry: BrowserEntry): Boolean =
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, entry.uri) }
            .getOrDefault(false)

    companion object {
        private const val KEY_TREE_URI = "tree_uri"
    }
}
