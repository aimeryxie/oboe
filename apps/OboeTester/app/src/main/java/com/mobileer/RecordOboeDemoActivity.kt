package com.mobileer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.harman.jblrecord.JblAudioFormat
import com.harman.jblrecord.JblChannelCount
import com.harman.jblrecord.JblMultiChannelRecord
import com.mobileer.oboetester.R
import com.mobileer.oboetester.RecorderActivity
import com.mobileer.oboetester.databinding.ActivityRecordOboeDemoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class RecordOboeDemoActivity : AppCompatActivity(), JblMultiChannelRecord.AudioCallback {


    companion object {
        const val TAG = "RecordOboeDemoActivity"
    }

    private val bind by lazy {
        ActivityRecordOboeDemoBinding.inflate(layoutInflater)
    }

    private var util: JblMultiChannelRecord = JblMultiChannelRecord()
    private var queue = LinkedBlockingQueue<ByteArray>()
    private var saveFileThread: Thread? = null
    private var startTime = System.currentTimeMillis()
    private var totalSize = 0
    private var logTime = System.currentTimeMillis()
    private var path = ""

    private var channel: JblChannelCount = JblChannelCount.Channel4
    private var format: JblAudioFormat = JblAudioFormat.I32
    private val sampleRate = 48000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(bind.root)
        this.setTitle("Jbl Multi-Channel Record")
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bind.rgChannel.setOnCheckedChangeListener { group, checkedId ->
            channel = when (checkedId) {
                R.id.rb_1_channel -> JblChannelCount.Mono
                R.id.rb_2_channel -> JblChannelCount.Stereo
                R.id.rb_4_channel -> JblChannelCount.Channel4
                else -> JblChannelCount.Unspecified
            }
            Log.i(TAG, "channel=${channel}")
        }
        bind.rgFormat.setOnCheckedChangeListener { group, checkedId ->
            format = when (checkedId) {
                R.id.rb_16_format -> JblAudioFormat.I16
                R.id.rb_24_format -> JblAudioFormat.I24
                R.id.rb_32_format -> JblAudioFormat.I32
                else -> JblAudioFormat.I32
            }
            Log.i(TAG, "format=${format}")
        }

        initView()
        util.init(
            this.applicationContext,
            sampleRate = sampleRate,
            channels = channel.value,
            format = format.value
        )

        bind.btStartRecord.setOnClickListener {
            if (!isRecordPermissionGranted()) {
                requestRecordPermission()
                Toast.makeText(this, "need request record permission", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            util.init(
                this.applicationContext,
                sampleRate = sampleRate,
                channels = channel.value,
                format = format.value
            )
            path = "/data/data/${this.packageName}/files/jbl_${channel}_${format}.wav"
            startTime = System.currentTimeMillis()
            totalSize = 0
            bind.tvInfo.text = "speed:0"
            Log.i(TAG, "startRecord=${channel},${format},${path}")
            util.startRecording(this)
            startSaveFile()
        }
        bind.btStopRecord.setOnClickListener {
            util.stopRecording()
            saveFileThread?.interrupt()
            saveFileThread = null
            bind.tvInfo.text = "stop=${path}"
            Log.i(TAG, "stop=${path}")
        }
        bind.btToHistoryRecord.setOnClickListener {
            RecorderActivity.launch(this)
        }


    }

    private fun startSaveFile() {
        saveFileThread?.interrupt()
        saveFileThread = thread {
            Log.i(TAG, "${Thread.currentThread().isInterrupted}")
            val bout = ByteArrayOutputStream()
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val datas = queue.take()
                    bout.write(datas)
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e1: Exception) {
                e1.printStackTrace()
            }

            Log.i(TAG, "save file thread end ${path}")
            val pcmDatas = bout.toByteArray()
            util.saveWavFile(
                path, pcmDatas,
                sampleRate,
                channels = channel.value,
                formatToValue()
            )
            bout.close()
        }
    }

    private fun formatToValue(): Int {
        return when (format) {
            JblAudioFormat.I16 -> 16
            JblAudioFormat.I24 -> 24
            JblAudioFormat.I32 -> 32
        }
    }

    private fun initView() {
        bind.rb4Channel.isChecked = true
        bind.rb32Format.isChecked = true
    }


    @OptIn(ExperimentalStdlibApi::class)
    override fun onAudioReady(audioData: ByteArray) {
        totalSize += audioData.size
        queue.offer(audioData.copyOf())
        val totalTime = System.currentTimeMillis() - startTime
        if (System.currentTimeMillis() - logTime > 5000) {
            logTime = System.currentTimeMillis()
            val speed = (totalSize / (totalTime / 1000f))/1024f
            lifecycleScope.launch(Dispatchers.IO) {
                bind.tvInfo.text = "speed:${speed}kb"
            }
            Log.i(
                TAG,
                "onAudioReady=queueSize=${queue.size},datasize=${audioData.size},speed=${speed},${audioData.toHexString()}"
            )
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        saveFileThread?.interrupt()
    }

    private fun isRecordPermissionGranted(): Boolean {
        return (ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED)
    }

    private fun requestRecordPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            0xFFFFFF
        )
    }


}