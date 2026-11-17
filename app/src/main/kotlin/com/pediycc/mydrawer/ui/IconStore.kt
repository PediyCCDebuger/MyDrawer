package com.pediycc.mydrawer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** 把用户从本机图库选择的图片复制到应用私有目录，避免 SAF Uri 授权过期后图标丢失。 */
class IconStore(private val context: Context) {
    private val dir = File(context.filesDir, "icons").apply { mkdirs() }

    fun copy(uri: Uri): String? = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val name = "icon_${UUID.randomUUID()}.png"
        val out = File(dir, name)
        FileOutputStream(out).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        out.absolutePath
    } catch (e: Exception) {
        null
    }

    fun load(key: String?): Bitmap? {
        if (key == null || !key.startsWith("file:")) return null
        return try {
            BitmapFactory.decodeFile(key.removePrefix("file:"))
        } catch (e: Exception) {
            null
        }
    }
}
