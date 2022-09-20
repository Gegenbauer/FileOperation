package com.gegenbauer.fileoperation

import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.util.*

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private val createFileBt by lazy { findViewById<Button>(R.id.create_file) }
    private val moveFileBt by lazy { findViewById<Button>(R.id.move_file) }

    private val internalFilePath by lazy { filesDir.absolutePath + File.separator }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createFileBt.setOnClickListener(this)
        moveFileBt.setOnClickListener(this)
    }

    private suspend fun moveFileToPublicDir() = withContext(Dispatchers.IO) {
        val fileDir = File(internalFilePath)
        fileDir.listFiles()?.forEach { file ->
            moveSingleFileToPublicDir(file)
        }
    }

    private fun moveSingleFileToPublicDir(file: File): Boolean {
        // 拿到 MediaStore.Images 表的uri
        val tableUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val value = ContentValues()
        value.put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
        value.put(MediaStore.MediaColumns.TITLE, file.name)

        // 将该索引信息插入数据表，获得图片的Uri
        val imageUri = contentResolver.insert(tableUri, value)
        imageUri ?: return false
        return kotlin.runCatching {
            val pfd = contentResolver.openFileDescriptor(imageUri, "w")
            pfd ?: return false
            val outStream = FileOutputStream(pfd.fileDescriptor);
            val inSteam = FileInputStream(file)
            val buf = ByteArray(8192)
            var len = inSteam.read(buf)
            while (len > 0) {
                outStream.write(buf, 0, len)
                len = inSteam.read(buf)
            }

            outStream.flush()
            outStream.close()
            inSteam.close()
            pfd.close()
            true
        }.getOrElse {
            Log.d(TAG, it.message.toString())
            false
        }
    }

    private suspend fun createRandomFile() = withContext(Dispatchers.IO) {
        val randomFileName = "${internalFilePath}${System.currentTimeMillis()}_${Random().nextInt(100)}.txt"
        kotlin.runCatching {
            val file = File(randomFileName)
            if (file.exists()) {
                file.delete()
            } else {
                file.createNewFile()
                val fileWriter = FileWriter(file)
                fileWriter.write(randomFileName)
                fileWriter.flush()
            }
        }.onFailure {
            showToast("random file created failed")
        }.onSuccess {
            showToast("random file created success, filename=${randomFileName}")
        }
    }

    private suspend fun showToast(str: String) = withContext(Dispatchers.Main) {
        Toast.makeText(this@MainActivity, str, Toast.LENGTH_SHORT).show()
    }

    override fun onClick(v: View) {
        lifecycleScope.launch {
            when (v.id) {
                R.id.create_file -> {
                    createRandomFile()
                }
                R.id.move_file -> {
                    moveFileToPublicDir()
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}