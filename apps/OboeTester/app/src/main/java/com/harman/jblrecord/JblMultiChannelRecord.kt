package com.harman.jblrecord

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import java.io.File

/**
 * @ProjectName: jbl multi-channel record
 * @Package: com.harman.jblrecord
 * @ClassName: JblMultiChannelRecord
 * @Description:https://github.com/google/oboe/tree/main/docs
 * @Author: xiemingming
 * @Date: 2025/3/25
 */
@Keep
class JblMultiChannelRecord {
    companion object {
        const val TAG = "JblMultiChannelRecord"

        init {
            System.loadLibrary("jbl_multi_channel_record")
        }

        /*
         *#define ILOG 0
         *#define ELOG 1
         *#define DLOG 2
         */
        @JvmStatic
        @Keep
        fun jniLog(type: Int, tag: String, message: String) {
            Log.i(TAG, "type=${type},tag=${tag},message=${message}")
            val jniTag="JNITAG"
            when (type) {
                0 -> {
                    Log.i(jniTag, message)
                }

                1 -> {
                    Log.e(jniTag, message)
                }

                2 -> {
                    Log.d(jniTag, message)
                }

                else -> {
                    Log.i(jniTag, message)
                }
            }

        }

    }

    @Keep
    interface AudioCallback {
        fun onAudioReady(audioData: ByteArray)
    }

    external fun init(context: Context, sampleRate: Int, channels: Int, format: Int)
    external fun startRecording(callback: AudioCallback): Int
    external fun stopRecording()
    external fun exitRecord()


}