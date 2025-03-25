#include <oboe/Oboe.h>
#include <jni.h>
#include <android/log.h>

#define LOG_TAG "OboeRecorder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 全局 JavaVM 实例
JavaVM *globalJavaVm = nullptr;

class RecorderCallback : public oboe::AudioStreamDataCallback {
public:
    RecorderCallback(JNIEnv *env, jobject javaCallback) {
        // 全局引用 Kotlin 回调对象
        javaCallback_ = env->NewGlobalRef(javaCallback);
        jclass cls = env->GetObjectClass(javaCallback);
        onAudioReady_ = env->GetMethodID(cls, "onAudioReady", "([B)V");
    }

    ~RecorderCallback() {
        // 释放全局引用
        JNIEnv *env = getJNIEnv();
        if (javaCallback_ != nullptr) {
            env->DeleteGlobalRef(javaCallback_);
        }
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override {
        // 获取音频数据
        auto *data = static_cast<int32_t *>(audioData); // 假设音频格式为 16 位 PCM
        int32_t dataSize = numFrames * audioStream->getChannelCount();
       // LOGE("onAudioReady dataSize= %d",(dataSize * sizeof(int16_t)));
        // 将音频数据传递给 Kotlin 层

        JNIEnv *env = getJNIEnv();
        jbyteArray javaArray = env->NewByteArray(dataSize * sizeof(int32_t));
        env->SetByteArrayRegion(javaArray, 0, dataSize * sizeof(int32_t), reinterpret_cast<jbyte *>(data));
        env->CallVoidMethod(javaCallback_, onAudioReady_, javaArray);
        env->DeleteLocalRef(javaArray);

        return oboe::DataCallbackResult::Continue;
    }

private:
    jobject javaCallback_;
    jmethodID onAudioReady_;

    JNIEnv *getJNIEnv() {
        JNIEnv *env;
        if (globalJavaVm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("Failed to attach current thread");
            return nullptr;
        }
        return env;
    }
};

// 全局变量，用于管理 AudioStream
oboe::AudioStream *audioStream = nullptr;
RecorderCallback *recorderCallback = nullptr;

extern "C"
JNIEXPORT void JNICALL
Java_com_mobileer_JblRecordUtil_startRecording(JNIEnv *env, jobject thiz, jobject javaCallback) {
    // 创建 AudioStreamBuilder
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::I32) // 16 位 PCM
            ->setChannelCount(oboe::ChannelCount::CH4)
            ->setSampleRate(44100);

    // 创建回调对象
    recorderCallback = new RecorderCallback(env, javaCallback);
    builder.setDataCallback(recorderCallback);

    // 打开音频流
    oboe::Result result = builder.openStream(&audioStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open stream: %s", oboe::convertToText(result));
        return;
    }

    // 开始录音
    audioStream->requestStart();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_mobileer_JblRecordUtil_stopRecording(JNIEnv *env, jobject thiz) {
    if (audioStream != nullptr) {
        // 停止录音
        audioStream->stop();
        audioStream->close();
        delete audioStream;
        audioStream = nullptr;
    }

    if (recorderCallback != nullptr) {
        // 释放回调对象
        delete recorderCallback;
        recorderCallback = nullptr;
    }
}

// JNI_OnLoad 方法，用于获取 JavaVM 实例
extern "C"
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    globalJavaVm = vm;
    return JNI_VERSION_1_6;
}
