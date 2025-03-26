package com.harman.jblrecord

import android.util.Log
import kotlin.math.abs

/**
 *
 * @ProjectName: OboeTester
 * @Package: com.harman.jblrecord
 * @ClassName: PcmTools
 * @Description:
 * @Author: mixie
 * @CreateDate: 2025/3/26 14:43
 * @UpdateUser:
 * @UpdateDate: 2025/3/26 14:43
 * @UpdateRemark:
 * @Version: 1.0
 */


object PcmTools {
//    @Throws(IOException::class)
//    @JvmStatic
//    fun main(args: Array<String>) {
//        val pcmData: ByteArray = Files.readAllBytes(Paths.get("raw.pcm"))
//        analyzePCM(pcmData)
//    }
    const val TAG="PcmTools"

    fun analyzePCM(pcmData: ByteArray) {
        // 假设数据是 16-bit 或 32-bit，尝试推断
        val bitDepth: Int
        val channels: Int

        // 检查是否为 16-bit（2字节/样本）
        if (pcmData.size % 2 == 0) {
            bitDepth = 16
            // 假设立体声（2通道），检查振幅变化
            channels = checkChannels(pcmData, bitDepth)
        } else if (pcmData.size % 4 == 0) {
            bitDepth = 32
            channels = checkChannels(pcmData, bitDepth)
        } else {
            throw IllegalArgumentException("无法推断位深度（可能是 24-bit 或其他格式）")
        }

        Log.i(TAG,"推断结果: $channels 通道, $bitDepth-bit")
    }

    // 通过振幅变化判断通道数
    private fun checkChannels(data: ByteArray, bitDepth: Int): Int {
        val bytesPerSample = bitDepth / 8
        val sample1 = bytesToInt(data, 0, bytesPerSample)
        val sample2 = bytesToInt(data, bytesPerSample, bytesPerSample)

        // 如果相邻样本值差异大，可能是多通道交替存储
        return if (abs((sample1 - sample2).toDouble()) > 1000) 2 else 1
    }

    // 将字节转换为整数（小端序）
    private fun bytesToInt(data: ByteArray, offset: Int, length: Int): Int {
        var value = 0
        for (i in 0 until length) {
            value = value or ((data[offset + i].toInt() and 0xFF) shl (8 * i))
        }
        return value
    }
}