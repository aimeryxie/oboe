package com.harman.jblrecord

/**
 *
 * @ProjectName: jbl multi-channel record
 * @Description:mapping to c++ lib
 * @Author: xiemingming
 * @CreateDate: 2025/3/26
 * @UpdateUser:
 * @UpdateDate: 2025/3/26
 * @UpdateRemark:
 * @Version: 1.0
 */
enum class JblChannelCount(val value: Int) {

    /**
     * Audio channel count definition, use Mono or Stereo、Channel4
     */
    Unspecified(0),

    /**
     * Use this for mono audio
     */
    Mono(1),

    /**
     * Use this for stereo audio.
     */
    Stereo(2),

    /**
     * Use this for 4 channel
     */
    Channel4(4),
}