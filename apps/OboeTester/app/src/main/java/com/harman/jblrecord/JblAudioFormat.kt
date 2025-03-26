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
enum class JblAudioFormat(val value: Int) {

    /**
     * Signed 16-bit integers.
     */
    I16(1), // AAUDIO_FORMAT_PCM_I16,

    /**
     * Signed 24-bit integers, packed into 3 bytes.
     *
     * Note that the use of this format does not guarantee that
     * the full precision will be provided.  The underlying device may
     * be using I16 format.
     *
     * Added in API 31 (S).
     */
    I24(3),// AAUDIO_FORMAT_PCM_I24_PACKED

    /**
     * Signed 32-bit integers.
     *
     * Note that the use of this format does not guarantee that
     * the full precision will be provided.  The underlying device may
     * be using I16 format.
     *
     * Added in API 31 (S).
     */
    I32(4)// AAUDIO_FORMAT_PCM_I32

}