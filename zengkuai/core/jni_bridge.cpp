/**
 * C++ JNI 桥接层 — 连接 Java Service 和 C++ 增块引擎
 *
 * 编译:
 *   g++ -shared -fPIC -o libzengkuai.so jni_bridge.cpp zengkuai.cpp \
 *       -I../include -I$JAVA_HOME/include -I$JAVA_HOME/include/linux \
 *       -std=c++17 -O3
 *
 * Windows (MSVC):
 *   cl /LD /Fe:zengkuai.dll jni_bridge.cpp zengkuai.cpp \
 *      /I../include /I%JAVA_HOME%/include /I%JAVA_HOME%/include/win32 /std:c++17 /O2
 */
#include "zengkuai.h"
#include <jni.h>
#include <string>
#include <cstring>

using namespace zengkuai;

// JSON 简易解析 (不引入第三方库，只提取关键字段)
static std::string jsonGet(const std::string& json, const std::string& key) {
    std::string search = "\"" + key + "\"";
    size_t pos = json.find(search);
    if (pos == std::string::npos) return "";
    pos = json.find(":", pos + search.length());
    if (pos == std::string::npos) return "";
    pos = json.find_first_not_of(" \t\n\"", pos + 1);
    if (pos == std::string::npos) return "";
    size_t end = json.find_first_of(",}\"\n", pos);
    return json.substr(pos, end - pos);
}

static DpiLog parseJson(const std::string& json) {
    DpiLog log;
    log.log_id      = jsonGet(json, "log_id");
    log.user_id     = jsonGet(json, "user_id");
    log.src_ip      = jsonGet(json, "src_ip");
    log.device_id   = jsonGet(json, "device_id");
    log.receiver_id = jsonGet(json, "receiver_id");
    log.event_type  = jsonGet(json, "event_type");
    log.event_status = jsonGet(json, "event_status");
    log.amount      = std::stod(jsonGet(json, "amount").empty() ? "0" : jsonGet(json, "amount"));

    // 时间戳优先用 timestamp_ms，否则用当前时间
    std::string ts = jsonGet(json, "timestamp_ms");
    log.timestamp_ms = ts.empty() ?
        std::chrono::duration_cast<std::chrono::seconds>(
            std::chrono::system_clock::now().time_since_epoch()).count() * 1000
        : std::stoll(ts);

    return log;
}

// ═══════════════════════════════════════════
//  JNI 导出函数
// ═══════════════════════════════════════════

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_antifraud_service_ZengKuaiService_nativeCreate(JNIEnv*, jobject) {
    auto* engine = new ZengKuaiEngine();
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_antifraud_service_ZengKuaiService_nativeDestroy(JNIEnv*, jobject, jlong ptr) {
    delete reinterpret_cast<ZengKuaiEngine*>(ptr);
}

JNIEXPORT void JNICALL
Java_com_antifraud_service_ZengKuaiService_nativeProcess(JNIEnv* env, jobject, jlong ptr, jstring jsonLog) {
    const char* str = env->GetStringUTFChars(jsonLog, nullptr);
    DpiLog log = parseJson(std::string(str));
    reinterpret_cast<ZengKuaiEngine*>(ptr)->process(log);
    env->ReleaseStringUTFChars(jsonLog, str);
}

JNIEXPORT jobject JNICALL
Java_com_antifraud_service_ZengKuaiService_nativeGetFeatures(JNIEnv* env, jobject, jlong ptr, jstring jsonLog) {
    const char* str = env->GetStringUTFChars(jsonLog, nullptr);
    DpiLog log = parseJson(std::string(str));
    FeatureVector fv = reinterpret_cast<ZengKuaiEngine*>(ptr)->getFeatures(log);
    env->ReleaseStringUTFChars(jsonLog, str);

    // 构建 HashMap<String, Double> 返回Java
    jclass mapClass = env->FindClass("java/util/HashMap");
    jmethodID init = env->GetMethodID(mapClass, "<init>", "()V");
    jmethodID put = env->GetMethodID(mapClass, "put",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    jobject map = env->NewObject(mapClass, init);

    // 使用宏简化重复代码
    #define PUT_FIELD(name, val) \
        env->CallObjectMethod(map, put, \
            env->NewStringUTF(name), \
            env->NewObject(env->FindClass("java/lang/Double"), \
                env->GetMethodID(env->FindClass("java/lang/Double"), "<init>", "(D)V"), (double)val))

    PUT_FIELD("user_login_count_1h", fv.user_login_count_1h);
    PUT_FIELD("user_login_count_24h", fv.user_login_count_24h);
    PUT_FIELD("user_failed_login_15m", fv.user_failed_login_15m);
    PUT_FIELD("user_transfer_count_1h", fv.user_transfer_count_1h);
    PUT_FIELD("user_transfer_count_24h", fv.user_transfer_count_24h);
    PUT_FIELD("user_transfer_amount_1h", fv.user_transfer_amount_1h);
    PUT_FIELD("user_transfer_amount_24h", fv.user_transfer_amount_24h);
    PUT_FIELD("user_unique_ips_24h", fv.user_unique_ips_24h);
    PUT_FIELD("user_unique_receivers_24h", fv.user_unique_receivers_24h);
    PUT_FIELD("user_unique_devices_24h", fv.user_unique_devices_24h);
    PUT_FIELD("user_max_amount_24h", fv.user_max_amount_24h);
    PUT_FIELD("ip_event_count_1h", fv.ip_event_count_1h);
    PUT_FIELD("ip_event_count_24h", fv.ip_event_count_24h);
    PUT_FIELD("ip_unique_users_24h", fv.ip_unique_users_24h);
    PUT_FIELD("device_event_count_1h", fv.device_event_count_1h);
    PUT_FIELD("device_unique_users_24h", fv.device_unique_users_24h);
    PUT_FIELD("receiver_transfer_count_1h", fv.receiver_transfer_count_1h);
    PUT_FIELD("receiver_transfer_count_24h", fv.receiver_transfer_count_24h);
    PUT_FIELD("receiver_transfer_amount_1h", fv.receiver_transfer_amount_1h);
    PUT_FIELD("receiver_transfer_amount_24h", fv.receiver_transfer_amount_24h);
    PUT_FIELD("receiver_unique_users_24h", fv.receiver_unique_users_24h);
    PUT_FIELD("global_rate", fv.global_rate);
    #undef PUT_FIELD

    return map;
}

JNIEXPORT jint JNICALL
Java_com_antifraud_service_ZengKuaiService_nativeBucketCount(JNIEnv*, jobject, jlong ptr) {
    return (jint)reinterpret_cast<ZengKuaiEngine*>(ptr)->totalBuckets();
}

JNIEXPORT void JNICALL
Java_com_antifraud_service_ZengKuaiService_nativeCleanup(JNIEnv*, jobject, jlong ptr, jint maxAgeMinutes) {
    reinterpret_cast<ZengKuaiEngine*>(ptr)->cleanup(maxAgeMinutes);
}

} // extern "C"
