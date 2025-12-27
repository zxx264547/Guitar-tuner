#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <memory>
#include <thread>
#include <vector>

#include <oboe/Oboe.h>

namespace {

constexpr const char *kTag = "TunerNative";

struct RecorderState {
    JavaVM *vm = nullptr;
    jobject engine = nullptr;
    jmethodID onPcm = nullptr;
    jmethodID onStreamConfig = nullptr;
    std::shared_ptr<oboe::AudioStream> stream;
    std::thread thread;
    std::atomic<bool> running{false};
    int32_t framesPerRead = 0;
};

RecorderState gState;

void logWarning(const char *message) {
    __android_log_write(ANDROID_LOG_WARN, kTag, message);
}

oboe::Result openStream(int32_t requestedSampleRate, int32_t framesPerRead) {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::I16)
            ->setSampleRate(requestedSampleRate)
            ->setChannelCount(oboe::ChannelCount::Mono);

    oboe::Result result = builder.openStream(gState.stream);
    if (result != oboe::Result::OK) {
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(gState.stream);
    }
    if (result != oboe::Result::OK) {
        gState.stream.reset();
        return result;
    }

    gState.framesPerRead = framesPerRead;
    if (gState.framesPerRead <= 0) {
        gState.framesPerRead = gState.stream->getFramesPerBurst();
    }
    return oboe::Result::OK;
}

void notifyStreamConfig(JNIEnv *env) {
    if (gState.engine == nullptr || gState.onStreamConfig == nullptr || gState.stream == nullptr) {
        return;
    }
    jint actualRate = static_cast<jint>(gState.stream->getSampleRate());
    env->CallVoidMethod(gState.engine, gState.onStreamConfig, actualRate);
}

void readLoop() {
    JNIEnv *env = nullptr;
    if (gState.vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
        logWarning("Failed to attach thread to JVM");
        return;
    }

    notifyStreamConfig(env);

    std::vector<int16_t> buffer(static_cast<size_t>(gState.framesPerRead));

    while (gState.running.load()) {
        if (!gState.stream) {
            break;
        }
        auto result = gState.stream->read(buffer.data(), gState.framesPerRead, 200000000);
        if (!result) {
            continue;
        }

        int32_t framesRead = result.value();
        if (framesRead <= 0) {
            continue;
        }

        jshortArray pcm = env->NewShortArray(framesRead);
        if (!pcm) {
            continue;
        }
        env->SetShortArrayRegion(pcm, 0, framesRead,
                                 reinterpret_cast<const jshort *>(buffer.data()));
        env->CallVoidMethod(gState.engine, gState.onPcm, pcm, framesRead);
        env->DeleteLocalRef(pcm);
    }

    gState.vm->DetachCurrentThread();
}

bool ensureJniRefs(JNIEnv *env, jobject thiz) {
    if (gState.engine == nullptr) {
        gState.engine = env->NewGlobalRef(thiz);
        if (!gState.engine) {
            logWarning("Failed to create global ref");
            return false;
        }
    }

    if (gState.onPcm == nullptr || gState.onStreamConfig == nullptr) {
        jclass cls = env->GetObjectClass(thiz);
        if (!cls) {
            logWarning("Failed to resolve class");
            return false;
        }
        gState.onPcm = env->GetMethodID(cls, "onPcm", "([SI)V");
        gState.onStreamConfig = env->GetMethodID(cls, "onStreamConfig", "(I)V");
        env->DeleteLocalRef(cls);
        if (!gState.onPcm || !gState.onStreamConfig) {
            logWarning("Failed to resolve JNI methods");
            return false;
        }
    }

    return true;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_tuner_TunerEngine_nativeStart(JNIEnv *env, jobject thiz,
                                               jint requestedSampleRate,
                                               jint framesPerRead) {
    if (gState.running.load()) {
        return JNI_TRUE;
    }
    if (!ensureJniRefs(env, thiz)) {
        return JNI_FALSE;
    }
    if (!gState.vm) {
        env->GetJavaVM(&gState.vm);
    }

    oboe::Result result = openStream(requestedSampleRate, framesPerRead);
    if (result != oboe::Result::OK || !gState.stream) {
        logWarning("Failed to open Oboe stream");
        return JNI_FALSE;
    }

    if (gState.stream->requestStart() != oboe::Result::OK) {
        logWarning("Failed to start Oboe stream");
        gState.stream->close();
        gState.stream.reset();
        return JNI_FALSE;
    }

    gState.running.store(true);
    gState.thread = std::thread(readLoop);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_tuner_TunerEngine_nativeStop(JNIEnv *env, jobject /*thiz*/) {
    gState.running.store(false);
    if (gState.stream) {
        gState.stream->requestStop();
    }
    if (gState.thread.joinable()) {
        gState.thread.join();
    }
    if (gState.stream) {
        gState.stream->close();
        gState.stream.reset();
    }

    if (gState.engine) {
        env->DeleteGlobalRef(gState.engine);
        gState.engine = nullptr;
    }
    gState.onPcm = nullptr;
    gState.onStreamConfig = nullptr;
}
