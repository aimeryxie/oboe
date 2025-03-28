package com.mobileer

import android.Manifest
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class RecordOboeDemoActivity : AppCompatActivity(), JblMultiChannelRecord.AudioCallback {


    companion object {
        const val TAG = "RecordOboeDemoActivity"

        // 创建 WAV 文件头
        private fun createWavHeader(pcmData: ByteArray, sampleRate: Int, channels: Int,format:Int): ByteArray {
            val dataSize = pcmData.size
            val header = ByteArray(44)

            // RIFF header
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()

            // File size (data size + 36)
            val fileSize = dataSize + 36
            header[4] = (fileSize and 0xFF).toByte()
            header[5] = (fileSize shr 8 and 0xFF).toByte()
            header[6] = (fileSize shr 16 and 0xFF).toByte()
            header[7] = (fileSize shr 24 and 0xFF).toByte()

            // WAVE header
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()

            // fmt chunk
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()

            // fmt chunk size (16 for PCM)
            header[16] = 16
            header[17] = 0
            header[18] = 0
            header[19] = 0

            // Audio format (1 for PCM)
            header[20] = 1
            header[21] = 0

            // Number of channels
            header[22] = channels.toByte()
            header[23] = 0

            // Sample rate
            header[24] = (sampleRate and 0xFF).toByte()
            header[25] = (sampleRate shr 8 and 0xFF).toByte()
            header[26] = (sampleRate shr 16 and 0xFF).toByte()
            header[27] = (sampleRate shr 24 and 0xFF).toByte()

            // Byte rate (sampleRate * channels * bitsPerSample / 8)
            val byteRate = sampleRate * channels * 16 / 8
            header[28] = (byteRate and 0xFF).toByte()
            header[29] = (byteRate shr 8 and 0xFF).toByte()
            header[30] = (byteRate shr 16 and 0xFF).toByte()
            header[31] = (byteRate shr 24 and 0xFF).toByte()

            // Block align (channels * bitsPerSample / 8)
            header[32] = (channels * 16 / 8).toByte()
            header[33] = 0

            // Bits per sample (16 for PCM)
            header[34] = format.toByte()
            header[35] = 0

            // data chunk
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()

            // data size
            header[40] = (dataSize and 0xFF).toByte()
            header[41] = (dataSize shr 8 and 0xFF).toByte()
            header[42] = (dataSize shr 16 and 0xFF).toByte()
            header[43] = (dataSize shr 24 and 0xFF).toByte()

            return header
        }

        //    // 保存 WAV 文件
        fun saveWavFile(filePath: String, pcmData: ByteArray, sampleRate: Int, channels: Int,format:Int) {
            val wavHeader = createWavHeader(pcmData, sampleRate, channels,format)
            val file = File(filePath)
            file.outputStream().use { outputStream ->
                outputStream.write(wavHeader)
                outputStream.write(pcmData)
            }
        }
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
    private var sampleRate = 48000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(bind.root)
        this.setTitle("Jbl Multi-Channel Record")
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setupAudioDeviceCallback()

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
            sampleRate=bind.etSampleRate.text.toString().toInt()
            path = "/data/data/${this.packageName}/files/jbl_${channel}_${format}_${sampleRate}.wav"
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
            saveWavFile(
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
            lifecycleScope.launch(Dispatchers.Main) {
                bind.tvInfo.text = "speed:${speed.toInt()}kb"
            }
            Log.i(
                TAG,
                "onAudioReady=queueSize=${queue.size},datasize=${audioData.size},speed=${speed.toInt()}KB,${audioData.toHexString()}}"//${audioData.toHexString()}
            )
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        saveFileThread?.interrupt()
        util.exitRecord();
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

    @TargetApi(23)
    private fun setupAudioDeviceCallback() {
        // Note that we will immediately receive a call to onDevicesAdded with the list of
        // devices which are currently connected.
        val mAudioManager = this.getSystemService(AUDIO_SERVICE) as AudioManager
        mAudioManager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {

                addedDevices.forEachIndexed { index, audioDeviceInfo ->
                    Log.i(TAG,"add device${index}=" +
                            "id=${audioDeviceInfo.id}," +
                            "name=${audioDeviceInfo.productName}," +
                            "desc=${audioDeviceInfo.audioDescriptors}," +
                            "channelCount=${audioDeviceInfo.channelCounts.toList()}," +
                            "sampleRates=${audioDeviceInfo.sampleRates.toList()}," +
                            "${audioDeviceInfo.audioProfiles.toList()}")
                }
                Log.i(TAG,"")

            }
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                removedDevices.forEachIndexed { index, audioDeviceInfo ->
                    Log.i(TAG,"remove device${index}=" +
                            "id=${audioDeviceInfo.id}," +
                            "name=${audioDeviceInfo.productName}," +
                            "desc=${audioDeviceInfo.audioDescriptors}," +
                            "channelCount=${audioDeviceInfo.channelCounts.toList()}," +
                            "sampleRates=${audioDeviceInfo.sampleRates.toList()}," +
                            "Profiles=${audioDeviceInfo.audioProfiles.toList()}")
                }
                Log.i(TAG,"")
            }
        }, null)
    }


}