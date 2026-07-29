package com.willmeet.musicplayer.library

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.willmeet.musicplayer.model.Track

/**
 * 通过 SAF 递归扫描用户授权的目录。
 *
 * 没走 MediaStore：那会把微信语音、铃声、游戏音效全扫进来，与桌面版「只看这个文件夹」
 * 的语义不符。SAF 的 tree URI 还能持久化授权，重启后依然有效，且不需要存储权限。
 *
 * 用 [DocumentsContract] 批量查询而不是 `DocumentFile.listFiles()` —— 后者对每个
 * 文件都要单独跨进程查一次属性，几千首歌能慢到几十秒。
 */
object LibraryScanner {

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "mka", "aiff", "aif"
    )

    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )

    /** 扫描过程中先收集的原始条目。 */
    private data class Entry(
        val documentId: String,
        val name: String,
        val parentId: String,
        val isDirectory: Boolean
    )

    /**
     * 扫描 [treeUri] 下的全部音频，并把同目录同名的 `.lrc` 配对进去。
     *
     * 目录不可读、授权失效等情况一律返回已收集到的部分，不抛异常 ——
     * 扫描不该因为某个子目录出问题就整体失败。
     */
    fun scan(context: Context, treeUri: Uri): List<Track> {
        val entries = mutableListOf<Entry>()

        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return emptyList()

        collect(context, treeUri, rootId, entries, depth = 0)

        // 歌词按「目录 + 文件名主干」建索引，供音频文件查找
        val lrcByKey = entries
            .asSequence()
            .filter { !it.isDirectory && it.name.substringAfterLast('.', "").equals("lrc", true) }
            .associateBy({ it.parentId to it.name.substringBeforeLast('.').lowercase() }) { it.documentId }

        return entries
            .asSequence()
            .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS }
            .map { entry ->
                val stem = entry.name.substringBeforeLast('.')
                val lrcId = lrcByKey[entry.parentId to stem.lowercase()]

                Track(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.documentId).toString(),
                    fileName = entry.name,
                    lrcUri = lrcId?.let {
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, it).toString()
                    },
                    parentId = entry.parentId
                )
            }
            .sortedWith(compareBy(NaturalOrder) { "${it.parentId}/${it.fileName}" })
            .toList()
    }

    /**
     * 深度优先遍历。
     *
     * [depth] 是防护性的：SAF 提供方理论上可以造出循环引用（例如指向自身的快捷方式），
     * 没有上限的话会无限递归。
     */
    private fun collect(
        context: Context,
        treeUri: Uri,
        parentId: String,
        into: MutableList<Entry>,
        depth: Int
    ) {
        if (depth > MAX_DEPTH) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)

        val cursor: Cursor = try {
            context.contentResolver.query(childrenUri, PROJECTION, null, null, null)
        } catch (e: Exception) {
            // 授权失效 / 提供方崩溃 / 目录消失，跳过这一支
            null
        } ?: return

        val subdirectories = mutableListOf<String>()

        cursor.use { c ->
            val idIndex = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (c.moveToNext()) {
                val id = c.getString(idIndex) ?: continue
                val name = c.getString(nameIndex) ?: continue
                val mime = c.getString(mimeIndex)

                val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR

                // 隐藏目录（.thumbnails 之类）跳过，和桌面版行为一致
                if (isDirectory && name.startsWith(".")) continue

                into += Entry(id, name, parentId, isDirectory)
                if (isDirectory) subdirectories += id
            }
        }

        // 游标关闭后再递归，避免同时打开过多游标
        subdirectories.forEach { collect(context, treeUri, it, into, depth + 1) }
    }

    private const val MAX_DEPTH = 24
}
