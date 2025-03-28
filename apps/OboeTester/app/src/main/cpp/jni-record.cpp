#include <oboe/Oboe.h>
#include <jni.h>
#include <android/log.h>
#include <ranges>  // C++20 Ranges

#define LOG_TAG "Jbl_Jni_MultiChannelRecord"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ILOG 0
#define ELOG 1
#define DLOG 2

// 全局 JavaVM 实例
JavaVM *globalJavaVm = nullptr;

class RecorderCallback : public oboe::AudioStreamDataCallback {
public:
    RecorderCallback(JNIEnv *env, jobject javaCallback, oboe::AudioFormat format) {
        // 全局引用 Kotlin 回调对象
        this->format_ = format;
        javaCallback_ = env->NewGlobalRef(javaCallback);
        jclass cls = env->GetObjectClass(javaCallback);
        onAudioReady_ = env->GetMethodID(cls, "onAudioReady", "([B)V");
    }

    ~RecorderCallback() override {
        // 释放全局引用
        JNIEnv *env = getJNIEnv();
        if (javaCallback_ != nullptr) {
            env->DeleteGlobalRef(javaCallback_);
        }
    }

    oboe::DataCallbackResult
    onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override {
//        // 获取音频数据
//        auto *data = static_cast<int32_t *>(audioData); // 假设音频格式为 16 位 PCM  int16_t
//        int32_t dataSize = numFrames * audioStream->getChannelCount();
//        // 将音频数据传递给 Kotlin 层
//        JNIEnv *env = getJNIEnv();
//        jbyteArray javaArray = env->NewByteArray(dataSize * sizeof(int32_t));
//        env->SetByteArrayRegion(javaArray, 0, dataSize * sizeof(int32_t),
//                                reinterpret_cast<jbyte *>(data));
//        env->CallVoidMethod(javaCallback_, onAudioReady_, javaArray);
//        env->DeleteLocalRef(javaArray);
        this->callbackToKotin(audioStream, audioData, numFrames);
        return oboe::DataCallbackResult::Continue;
    }

private:
    jobject javaCallback_;
    jmethodID onAudioReady_;
    oboe::AudioFormat format_ = oboe::AudioFormat::I16;

    void callbackToKotin(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {
//        LOGD("callbackToKotin channel=%d,format=%d ,config ch=%d,format=%d,numFrames=%d",
//             audioStream->getHardwareChannelCount()
//             ,audioStream->getHardwareFormat(),
//             audioStream->getChannelCount(),
//             audioStream->getFormat(),numFrames);
//        oboe::ChannelCount count;
//        oboe::AudioFormat format;
        switch (format_) {
            case oboe::AudioFormat::I16: {
                auto *data = static_cast<int16_t *>(audioData);
                int16_t dataSize = numFrames * audioStream->getChannelCount();
                JNIEnv *env = getJNIEnv();
                jbyteArray javaArray = env->NewByteArray(dataSize * sizeof(int16_t));
                env->SetByteArrayRegion(javaArray, 0, dataSize * sizeof(int16_t),
                                        reinterpret_cast<jbyte *>(data));
                env->CallVoidMethod(javaCallback_, onAudioReady_, javaArray);
                env->DeleteLocalRef(javaArray);
                break;
            }

            case oboe::AudioFormat::I24: {
                // 24-bit 处理（每个样本占 3 字节）
                // 获取 24-bit 音频数据（每个样本占 3 字节）
                uint8_t *data = static_cast<uint8_t *>(audioData);
                int32_t numSamples = numFrames * audioStream->getChannelCount();
                int32_t totalBytes = numSamples * 3; // 24-bit = 3 bytes/sample
                // 将数据拷贝到 ByteArray
                JNIEnv *env = getJNIEnv();
                jbyteArray javaArray = env->NewByteArray(totalBytes);
                env->SetByteArrayRegion(javaArray, 0, totalBytes, reinterpret_cast<jbyte *>(data));
                // 回调到 Kotlin
                env->CallVoidMethod(javaCallback_, onAudioReady_, javaArray,
                                    audioStream->getSampleRate(), audioStream->getChannelCount());
                env->DeleteLocalRef(javaArray);
                break;
            }
            case oboe::AudioFormat::I32: {
                auto *data = static_cast<int32_t *>(audioData);
                int32_t dataSize = numFrames * audioStream->getChannelCount();
                JNIEnv *env = getJNIEnv();
                jbyteArray javaArray = env->NewByteArray(dataSize * sizeof(int32_t));
                env->SetByteArrayRegion(javaArray, 0, dataSize * sizeof(int32_t),
                                        reinterpret_cast<jbyte *>(data));
                env->CallVoidMethod(javaCallback_, onAudioReady_, javaArray);
                env->DeleteLocalRef(javaArray);
                break;
            }

            default:
                break;

        }
    }

    static JNIEnv *getJNIEnv() {
        JNIEnv *env;
        if (globalJavaVm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("Failed to attach current thread");
            return nullptr;
        }
        return env;
    }
};


// 全局变量，用于管理 AudioStream
oboe::AudioStream *audioStream_ = nullptr;
RecorderCallback *recorderCallback_ = nullptr;
jint sampleRate_ = 44100;
oboe::ChannelCount channels_ = oboe::ChannelCount::Mono;
oboe::AudioFormat format_ = oboe::AudioFormat::I16;
static jclass jblMultiChannelRecordClass = nullptr;
static jmethodID jniLogMethodID = nullptr;
const char *VERSION = "Jbl Record V1.0.0.2";

void exitRecord();

jstring getPackageName(JNIEnv *env, jobject thiz);

void callKotlinJniLog(JNIEnv *env, int type, const char *tag, const char *message);

void exitRecord() {
    if (audioStream_ != nullptr) {
        // 停止录音
        audioStream_->stop();
        audioStream_->close();
        delete audioStream_;
        audioStream_ = nullptr;
    }

    if (recorderCallback_ != nullptr) {
        // 释放回调对象
        delete recorderCallback_;
        recorderCallback_ = nullptr;
    }

}

jstring getPackageName(JNIEnv *env, jobject thiz) {
    jclass activityClass = env->GetObjectClass(thiz);
    jmethodID getPackageNameMethod = env->GetMethodID(
            activityClass,
            "getPackageName",
            "()Ljava/lang/String;"
    );
    return (jstring) env->CallObjectMethod(thiz, getPackageNameMethod);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_harman_jblrecord_JblMultiChannelRecord_startRecording(JNIEnv *env, jobject thiz,
                                                               jobject javaCallback) {
    exitRecord();
    // 创建 AudioStreamBuilder
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(format_)
            ->setChannelCount(channels_)
            ->setSampleRate(sampleRate_);

    // 创建回调对象
    recorderCallback_ = new RecorderCallback(env, javaCallback, format_);
    builder.setDataCallback(recorderCallback_);
    callKotlinJniLog(env, DLOG, LOG_TAG, "startRecord");
    // 打开音频流
    oboe::Result result = builder.openStream(&audioStream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open stream: %s", oboe::convertToText(result));
        return -1;
    }

    LOGD("device ID=%d", audioStream_->getDeviceId());
    // 开始录音
    oboe::Result startResult = audioStream_->requestStart();
    if (startResult != oboe::Result::OK) {
        LOGE("Failed to requeststart: %s", oboe::convertToText(result));
        return -1;
    }
    return 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_harman_jblrecord_JblMultiChannelRecord_stopRecording(JNIEnv *env, jobject thiz) {
    callKotlinJniLog(env, DLOG, LOG_TAG, "stopRecording");
    exitRecord();
}

// JNI_OnLoad 方法，用于获取 JavaVM 实例
extern "C"
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGD("JNI_OnLoad");
    globalJavaVm = vm;
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *vm, void *reserved) {
    printf("JNI_OnUnload");
    LOGD("JNI_OnUnload");
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return;
    }
    if (jblMultiChannelRecordClass != nullptr) {
        env->DeleteGlobalRef(jblMultiChannelRecordClass);
        jblMultiChannelRecordClass = nullptr;
    }
}


extern "C"
JNIEXPORT void JNICALL
Java_com_harman_jblrecord_JblMultiChannelRecord_exitRecord(JNIEnv *env, jobject thiz) {
    callKotlinJniLog(env, DLOG, LOG_TAG, "exitRecord");
    exitRecord();
    LOGD("Exit jni record");
    if (jblMultiChannelRecordClass != nullptr) {
        env->DeleteGlobalRef(jblMultiChannelRecordClass);
        jblMultiChannelRecordClass = nullptr;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_harman_jblrecord_JblMultiChannelRecord_init(JNIEnv *env, jobject thiz, jobject context,
                                                     jint sample_rate, jint channels,
                                                     jint format) {
    sampleRate_ = sample_rate;
    channels_ = (oboe::ChannelCount) channels;
    format_ = (oboe::AudioFormat) format;
    callKotlinJniLog(env, DLOG, LOG_TAG, VERSION);
    LOGI("jblRecord version:%s at 2025-03-28 01", VERSION);
    jstring packageName = getPackageName(env, context);
    if (packageName == nullptr) {
        LOGE("init jbl record packageName is null");
        return;
    }
    jint jformat;
    switch (format_) {
        case oboe::AudioFormat::I16: {
            jformat = 16;
            break;
        }
        case oboe::AudioFormat::I24: {
            jformat = 24;
            break;
        }
        case oboe::AudioFormat::I32: {
            jformat = 32;
            break;
        }
        default: {
            jformat = -1;
            break;
        }
    }
    LOGD("init jbl record config sampleRate=%d,channels=%d,format=%d", sampleRate_, channels_,
         jformat);

    // 2. 将 jstring 转换为 C 字符串
    const char *cStr = env->GetStringUTFChars(packageName, nullptr);
    if (cStr == nullptr) {
        LOGE("init jbl record Failed to convert Java string to UTF-8!");
        return;
    }

    // 3. 定义目标字符串
    const char *targetStr = "com.jbl.oneapp"; // "com.harmankardon.oneapp";

    // 4. 比较字符串
    jboolean isEqual = (strcmp(cStr, targetStr) == 0);

    // 5. 释放内存
    env->ReleaseStringUTFChars(packageName, cStr);

    LOGD("init jbl record end packageName is %s ", cStr);
    if (!isEqual) {
        LOGE("[warning]: packageName must [%s],current is [%s] not match", targetStr, cStr);
       // env->NewString();
        //callKotlinJniLog(env,ELOG,LOG_TAG,"[warning]: packageName must [%s],current is [%s] not match");
        //exit(0);
    }
}


void callKotlinJniLog(JNIEnv *env, int type, const char *tag, const char *message) {
    // 1. 检查全局类引用是否已缓存
    // 1. 查找类（首次调用时缓存）
    if (jblMultiChannelRecordClass == nullptr) {
        jclass localClass = env->FindClass(
                "com/harman/jblrecord/JblMultiChannelRecord");//$Companion
        if (!localClass) {
            LOGE("dont find class [com/harman/jblrecord/JblMultiChannelRecord]");
            return;
        }
        jblMultiChannelRecordClass = (jclass) env->NewGlobalRef(localClass);
        env->DeleteLocalRef(localClass);
    }

    // 2. 检查方法ID是否已缓存
    if (jniLogMethodID == nullptr) {
        jniLogMethodID = env->GetStaticMethodID(
                jblMultiChannelRecordClass,
                "jniLog",
                "(ILjava/lang/String;Ljava/lang/String;)V"
        );
        if (jniLogMethodID == nullptr) {
            LOGE("Failed to find jniLog method");
            return;
        }
    }

    // 3. 将C字符串转换为Java的String对象
    jstring jTag = env->NewStringUTF(tag);
    jstring jMessage = env->NewStringUTF(message);

    // 4. 调用静态方法
    env->CallStaticVoidMethod(
            jblMultiChannelRecordClass,
            jniLogMethodID,
            (jint) type,
            jTag,
            jMessage
    );

    // 5. 释放局部引用
    env->DeleteLocalRef(jTag);
    env->DeleteLocalRef(jMessage);
}




