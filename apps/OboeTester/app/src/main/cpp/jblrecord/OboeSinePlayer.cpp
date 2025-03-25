//
// Created by xiemingming on 2025/3/25.
//
#include <jni.h>
#include "OboeSinePlayer.h"


// Call this from Activity onResume()
int32_t OboeSinePlayer::startAudio(JNIEnv *env) {
    this->globalEnv=env;
    std::lock_guard<std::mutex> lock(mLock);
    oboe::AudioStreamBuilder builder;
    // The builder set methods can be chained for convenience.
    oboe::Result result = builder.setSharingMode(oboe::SharingMode::Exclusive)
            ->setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setChannelCount(kChannelCount)
            ->setSampleRate(kSampleRate)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setFormat(oboe::AudioFormat::Float)
            ->setDataCallback(this)
            ->openStream(mStream);
    if (result != oboe::Result::OK) return (int32_t) result;

    // Typically, start the stream after querying some stream information, as well as some input from the user
    result = mStream->requestStart();
    return (int32_t) result;
}

// Call this from Activity onPause()
void OboeSinePlayer::stopAudio() {
    // Stop, close and delete in case not already closed.
    std::lock_guard<std::mutex> lock(mLock);
    if (mStream) {
        mStream->stop();
        mStream->close();
        mStream.reset();
    }
    LOGD("stopAudio %s","stop");
}




