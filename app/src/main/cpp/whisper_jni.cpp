#include <jni.h>
#include <algorithm>
#include <string>
#include <thread>
#include <vector>
#include "whisper.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileagent_voice_NativeWhisperTranscriber_nativeTranscribe(
    JNIEnv *env,
    jobject,
    jstring model_path_value,
    jshortArray samples_value,
    jint sample_rate
) {
    const char *model_path_chars = env->GetStringUTFChars(model_path_value, nullptr);
    const std::string model_path = model_path_chars == nullptr ? "" : model_path_chars;
    if (model_path_chars != nullptr) env->ReleaseStringUTFChars(model_path_value, model_path_chars);

    jsize sample_count = env->GetArrayLength(samples_value);
    jshort *samples = env->GetShortArrayElements(samples_value, nullptr);
    if (samples == nullptr) return env->NewStringUTF("");
    std::vector<float> audio(static_cast<size_t>(sample_count));
    for (jsize index = 0; index < sample_count; ++index) {
        audio[static_cast<size_t>(index)] = static_cast<float>(samples[index]) / 32768.0f;
    }
    env->ReleaseShortArrayElements(samples_value, samples, JNI_ABORT);

    whisper_context_params context_params = whisper_context_default_params();
    context_params.use_gpu = false;
    whisper_context *context = whisper_init_from_file_with_params(model_path.c_str(), context_params);
    if (context == nullptr) return env->NewStringUTF("");

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "de";
    params.n_threads = std::max(1u, std::thread::hardware_concurrency());
    params.no_context = true;
    params.single_segment = false;

    std::string result;
    if (whisper_full(context, params, audio.data(), static_cast<int>(audio.size())) == 0) {
        const int segment_count = whisper_full_n_segments(context);
        for (int index = 0; index < segment_count; ++index) {
            const char *segment = whisper_full_get_segment_text(context, index);
            if (segment != nullptr) result += segment;
        }
    }
    whisper_free(context);
    return env->NewStringUTF(result.c_str());
}
