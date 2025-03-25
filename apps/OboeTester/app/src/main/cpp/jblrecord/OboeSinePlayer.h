//
// Created by xiemingming on 2025/3/25.
//

#ifndef OBOETESTER_OBOESINEPLAYER_H
#define OBOETESTER_OBOESINEPLAYER_H

#endif //OBOETESTER_OBOESINEPLAYER_H

#include <oboe/Oboe.h>
#include "common/OboeDebug.h"


class OboeSinePlayer : public oboe::AudioStreamDataCallback {

public:
    std::mutex mLock;
    std::shared_ptr<oboe::AudioStream> mStream;

// Stream params
    static int constexpr kChannelCount = 2;
    static int constexpr kSampleRate = 48000;
// Wave params, these could be instance variables in order to modify at runtime
    static float constexpr kAmplitude = 0.5f;
    static float constexpr kFrequency = 440;
    static float constexpr kPI = M_PI;
    static float constexpr kTwoPi = kPI * 2;
    static double constexpr mPhaseIncrement = kFrequency * kTwoPi / (double) kSampleRate;
// Keeps track of where the wave is
    float mPhase = 0.0;

    JNIEnv *globalEnv;

    oboe::DataCallbackResult
    onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) override {
        // 获取 PCM 数据
        auto *data = static_cast<int16_t *>(audioData); // 假设音频格式为 16 位 PCM
        int32_t dataSize = numFrames * oboeStream->getChannelCount();

        // 打印 PCM 数据
        for (int i = 0; i < dataSize; i++) {
            LOGI("PCM data[%d]: %d", i, data[i]);
        }

        return oboe::DataCallbackResult::Continue;
    }

public:
    int32_t startAudio(JNIEnv *env);
    void stopAudio();


};