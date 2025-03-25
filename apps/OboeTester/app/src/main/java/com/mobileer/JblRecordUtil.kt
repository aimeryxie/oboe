package com.mobileer

import java.io.File

/**
 * @ProjectName: OboeTester
 * @Package: com.mobileer
 * @ClassName: JblRecordUtil
 * @Description:
 * @Author: xiemingming
 * @Date: 2025/3/25 20:29
 */
class JblRecordUtil {
    companion object{
        init {
            System.loadLibrary("jbl_oboe_record")
        }
    }
    interface AudioCallback {
        fun onAudioReady(audioData: ByteArray)
    }


    external fun startRecording(callback: AudioCallback)
    external fun stopRecording()

    // 创建 WAV 文件头
    fun createWavHeader(pcmData: ByteArray, sampleRate: Int, channels: Int,bit:Int): ByteArray {
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
        header[34] = bit.toByte()
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
    fun saveWavFile(filePath: String, pcmData: ByteArray, sampleRate: Int, channels: Int,bit:Int) {
        val wavHeader = createWavHeader(pcmData, sampleRate, channels,bit)
        val file = File(filePath)
        file.outputStream().use { outputStream ->
            outputStream.write(wavHeader)
            outputStream.write(pcmData)
        }
    }
//
//    // 使用 MediaPlayer 播放 WAV 文件
//    val mediaPlayer = MediaPlayer().apply {
//        setDataSource("/sdcard/myfile.wav")
//        prepare()
//        start()
//    }

}