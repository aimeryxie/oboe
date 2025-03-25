package com.mobileer

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mobileer.oboetester.R
import com.mobileer.oboetester.RecorderActivity
import com.mobileer.oboetester.databinding.ActivityRecordOboeDemoBinding
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class RecordOboeDemoActivity : AppCompatActivity(), JblRecordUtil.AudioCallback {


    companion object {
        const val TAG = "RecordOboeDemoActivity"
    }

    private val bind by lazy {
        ActivityRecordOboeDemoBinding.inflate(layoutInflater)
    }

    private var util: JblRecordUtil = JblRecordUtil()

    var path = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(bind.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        path = "/data/data/${this.packageName}/files/xiemingming.wav"

        bind.btStartRecord.setOnClickListener {
            startTime = System.currentTimeMillis()
            util.startRecording(this)
        }
        bind.btStopRecord.setOnClickListener {
            util.stopRecording()
            saveFileThread?.interrupt()
            Log.i(TAG, "stop")
        }
        bind.btToHistoryRecord.setOnClickListener {
            RecorderActivity.launch(this)
        }



        saveFileThread = thread {
            Log.i(TAG, "${Thread.currentThread().isInterrupted}")


            val bout = ByteArrayOutputStream()
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val datas = queue.take()
                    bout.write(datas)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Log.i(TAG, "end ${path}")
            util.saveWavFile(path, bout.toByteArray(), 44100, 1)
            bout.close()
        }
    }

    private var queue = LinkedBlockingQueue<ByteArray>()

    private var saveFileThread: Thread? = null

    var startTime = System.currentTimeMillis()
    var totalSize = 0
    var logTime = System.currentTimeMillis()

    @OptIn(ExperimentalStdlibApi::class)
    override fun onAudioReady(audioData: ByteArray) {
        totalSize += audioData.size
        queue.offer(audioData.copyOf())
        val totalTime = System.currentTimeMillis() - startTime
        if (System.currentTimeMillis() - logTime > 5000) {
            logTime = System.currentTimeMillis()
            val speed = totalSize / (totalTime / 1000f)
            Log.i(
                TAG,
                "onAudioReady=${queue.size},${audioData.size},speed=${speed},${audioData.toHexString()}"
            )
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        saveFileThread?.interrupt()
    }


}