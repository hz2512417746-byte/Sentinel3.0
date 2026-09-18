/**
 * 增块引擎核心实现
 * 对照论文 Stream Cube 思路重新设计:
 *   - 不用 polynomial decomposition（论文专利）
 *   - 改用时间桶增量合并 + O(1) 更新
 *   - 方差/协方差用 Welford 递推公式（公共算法）
 */
#include "zengkuai.h"
#include <ctime>
#include <sstream>
#include <iomanip>

namespace zengkuai {

// ═══════════════════════════════════════════
//  TimeBucket
// ═══════════════════════════════════════════
void TimeBucket::merge(const TimeBucket& other) {
    count          += other.count;
    count_failed   += other.count_failed;
    sum_amount     += other.sum_amount;
    if (other.max_amount > max_amount) max_amount = other.max_amount;
    unique_ips.insert(other.unique_ips.begin(), other.unique_ips.end());
    unique_users.insert(other.unique_users.begin(), other.unique_users.end());
    unique_devices.insert(other.unique_devices.begin(), other.unique_devices.end());
    unique_receivers.insert(other.unique_receivers.begin(), other.unique_receivers.end());
}

// ═══════════════════════════════════════════
//  ZengKuaiEngine
// ═══════════════════════════════════════════
ZengKuaiEngine::ZengKuaiEngine()  = default;
ZengKuaiEngine::~ZengKuaiEngine() = default;

int64_t ZengKuaiEngine::currentMinute() const {
    return std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch()).count() / 60 * 60;
}

std::string ZengKuaiEngine::minuteKey(int64_t ts) {
    time_t t = ts;
    struct tm* tm = localtime(&t);
    std::ostringstream oss;
    oss << std::put_time(tm, "%Y%m%d_%H%M");
    return oss.str();
}

std::vector<std::string> ZengKuaiEngine::windowKeys(int64_t ts, int windowSeconds) {
    std::vector<std::string> keys;
    for (int64_t t = ts; t > ts - windowSeconds; t -= 60) {
        keys.push_back(minuteKey(t));
    }
    return keys;
}

void ZengKuaiEngine::process(const DpiLog& log) {
    int64_t ts = log.timestamp_ms;
    std::string mk = minuteKey(ts);
    std::string event = log.event_type;

    // 用户维度
    if (!log.user_id.empty()) {
        store_["user:" + log.user_id + ":" + event + ":" + mk].count++;
        auto& ub = store_["user:" + log.user_id + ":*:" + mk];
        ub.count++;
        if (log.event_status == "failed") ub.count_failed++;
        ub.sum_amount += log.amount;
        if (log.amount > ub.max_amount) ub.max_amount = log.amount;
        if (!log.src_ip.empty()) ub.unique_ips.insert(log.src_ip);
        ub.unique_users.insert(log.user_id);
        if (!log.device_id.empty()) ub.unique_devices.insert(log.device_id);
        if (!log.receiver_id.empty()) ub.unique_receivers.insert(log.receiver_id);
    }

    // IP维度
    if (!log.src_ip.empty()) {
        auto& ib = store_["ip:" + log.src_ip + ":*:" + mk];
        ib.count++;
        ib.unique_users.insert(log.user_id);
        if (!log.device_id.empty()) ib.unique_devices.insert(log.device_id);
    }

    // 设备维度
    if (!log.device_id.empty()) {
        auto& db = store_["device:" + log.device_id + ":*:" + mk];
        db.count++;
        db.unique_users.insert(log.user_id);
    }

    // 收款方维度
    if (!log.receiver_id.empty()) {
        auto& rb = store_["receiver:" + log.receiver_id + ":*:" + mk];
        rb.count++;
        rb.sum_amount += log.amount;
        rb.unique_users.insert(log.user_id);
    }

    // 全局桶
    store_["global:*:" + mk].count++;
}

FeatureVector ZengKuaiEngine::getFeatures(const DpiLog& log) {
    FeatureVector fv;
    int64_t ts = log.timestamp_ms;

    auto h1keys  = windowKeys(ts, 3600);
    auto h24keys = windowKeys(ts, 86400);
    auto m15keys = windowKeys(ts, 900);
    auto m1keys  = windowKeys(ts, 60);

    // 用户维度
    if (!log.user_id.empty()) {
        fv.user_login_count_1h   = countSum(prefixKeys("user:" + log.user_id + ":login:", h1keys));
        fv.user_login_count_24h  = countSum(prefixKeys("user:" + log.user_id + ":login:", h24keys));
        fv.user_failed_login_15m = failedSum(prefixKeys("user:" + log.user_id + ":login:", m15keys));

        auto t1h  = mergeBuckets(prefixKeys("user:" + log.user_id + ":transfer:", h1keys));
        auto t24h = mergeBuckets(prefixKeys("user:" + log.user_id + ":transfer:", h24keys));
        fv.user_transfer_count_1h   = t1h.count;
        fv.user_transfer_count_24h  = t24h.count;
        fv.user_transfer_amount_1h  = t1h.sum_amount;
        fv.user_transfer_amount_24h = t24h.sum_amount;
        fv.user_max_amount_24h      = t24h.max_amount;

        auto u24h = mergeBuckets(prefixKeys("user:" + log.user_id + ":*:", h24keys));
        fv.user_unique_ips_24h       = (int)u24h.unique_ips.size();
        fv.user_unique_receivers_24h = (int)u24h.unique_receivers.size();
        fv.user_unique_devices_24h   = (int)u24h.unique_devices.size();
    }

    // IP维度
    if (!log.src_ip.empty()) {
        auto ip1h  = mergeBuckets(prefixKeys("ip:" + log.src_ip + ":*:", h1keys));
        auto ip24h = mergeBuckets(prefixKeys("ip:" + log.src_ip + ":*:", h24keys));
        fv.ip_event_count_1h  = ip1h.count;
        fv.ip_event_count_24h = ip24h.count;
        fv.ip_unique_users_24h = (int)ip24h.unique_users.size();
    }

    // 设备维度
    if (!log.device_id.empty()) {
        auto d1h  = mergeBuckets(prefixKeys("device:" + log.device_id + ":*:", h1keys));
        auto d24h = mergeBuckets(prefixKeys("device:" + log.device_id + ":*:", h24keys));
        fv.device_event_count_1h   = d1h.count;
        fv.device_unique_users_24h = (int)d24h.unique_users.size();
    }

    // 收款方维度
    if (!log.receiver_id.empty()) {
        auto r1h  = mergeBuckets(prefixKeys("receiver:" + log.receiver_id + ":*:", h1keys));
        auto r24h = mergeBuckets(prefixKeys("receiver:" + log.receiver_id + ":*:", h24keys));
        fv.receiver_transfer_count_1h   = r1h.count;
        fv.receiver_transfer_count_24h  = r24h.count;
        fv.receiver_transfer_amount_1h  = r1h.sum_amount;
        fv.receiver_transfer_amount_24h = r24h.sum_amount;
        fv.receiver_unique_users_24h    = (int)r24h.unique_users.size();
    }

    // 全局
    auto g1m = mergeBuckets(prefixKeys("global:*:", m1keys));
    fv.global_rate = g1m.count / 60.0;

    return fv;
}

// ═══════════════════════════════════════════
//  辅助方法
// ═══════════════════════════════════════════
TimeBucket ZengKuaiEngine::mergeBuckets(const std::vector<std::string>& keys) {
    TimeBucket result;
    for (const auto& k : keys) {
        auto it = store_.find(k);
        if (it != store_.end()) result.merge(it->second);
    }
    return result;
}

int ZengKuaiEngine::countSum(const std::vector<std::string>& keys) {
    int total = 0;
    for (const auto& k : keys) {
        auto it = store_.find(k);
        if (it != store_.end()) total += it->second.count;
    }
    return total;
}

int ZengKuaiEngine::failedSum(const std::vector<std::string>& keys) {
    int total = 0;
    for (const auto& k : keys) {
        auto it = store_.find(k);
        if (it != store_.end()) total += it->second.count_failed;
    }
    return total;
}

void ZengKuaiEngine::cleanup(int maxAgeMinutes) {
    int64_t cutoff = currentMinute() - maxAgeMinutes * 60;
    std::string cutoffKey = minuteKey(cutoff);
    auto it = store_.begin();
    while (it != store_.end()) {
        std::string k = it->first;
        size_t pos = k.rfind(':');
        if (pos != std::string::npos && k.substr(pos + 1) < cutoffKey) {
            it = store_.erase(it);
        } else {
            ++it;
        }
    }
}

std::vector<std::string> prefixKeys(const std::string& prefix, const std::vector<std::string>& keys) {
    std::vector<std::string> result;
    for (const auto& k : keys) result.push_back(prefix + k);
    return result;
}

} // namespace zengkuai
