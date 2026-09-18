/**
 * 增块 (ZengKuai) — 增量特征计算引擎
 *
 * 设计思路（不同于论文的 Stream Cube）:
 *   - 时间分桶: 按分钟粒度切分，1分钟一个桶
 *   - 增量合并: 大时间窗口 = 合并多个1分钟桶，不重算
 *   - 多维索引: user_id / ip / device / receiver 四维独立聚合
 *   - 指标分解: 方差/协方差等复杂指标用增量公式递推
 *   - 内存紧凑: C风格数组+哈希表，避免Python对象开销
 *
 * 性能目标: 单条处理 < 2ms (C++ vs Python 30ms)
 */

#pragma once
#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>
#include <set>
#include <cmath>
#include <chrono>

namespace zengkuai {

// ─── 特征结构 ───
struct FeatureVector {
    // 用户维度
    int    user_login_count_1h      = 0;
    int    user_login_count_24h     = 0;
    int    user_failed_login_15m    = 0;
    int    user_transfer_count_1h   = 0;
    int    user_transfer_count_24h  = 0;
    double user_transfer_amount_1h  = 0.0;
    double user_transfer_amount_24h = 0.0;
    int    user_unique_ips_24h      = 0;
    int    user_unique_receivers_24h = 0;
    int    user_unique_devices_24h  = 0;
    double user_max_amount_24h      = 0.0;

    // IP维度
    int    ip_event_count_1h        = 0;
    int    ip_event_count_24h       = 0;
    int    ip_unique_users_24h      = 0;

    // 设备维度
    int    device_event_count_1h    = 0;
    int    device_unique_users_24h  = 0;

    // 收款方维度
    int    receiver_transfer_count_1h   = 0;
    int    receiver_transfer_count_24h  = 0;
    double receiver_transfer_amount_1h  = 0.0;
    double receiver_transfer_amount_24h = 0.0;
    int    receiver_unique_users_24h    = 0;

    // 全局
    double global_rate = 0.0;
};

// ─── 时间桶 ───
struct TimeBucket {
    int    count          = 0;
    int    count_failed   = 0;
    double sum_amount     = 0.0;
    double max_amount     = 0.0;
    std::set<std::string> unique_ips;
    std::set<std::string> unique_users;
    std::set<std::string> unique_devices;
    std::set<std::string> unique_receivers;

    void merge(const TimeBucket& other);
    void clear_expired();
};

// ─── 日志输入 ───
struct DpiLog {
    std::string log_id;
    std::string user_id;
    std::string src_ip;
    std::string device_id;
    std::string receiver_id;
    std::string event_type;
    std::string event_status;
    double      amount = 0.0;
    int64_t     timestamp_ms = 0;
};

// ─── 增块引擎 ───
class ZengKuaiEngine {
public:
    ZengKuaiEngine();
    ~ZengKuaiEngine();

    // 处理单条日志（增量更新所有相关桶）
    void process(const DpiLog& log);

    // 获取指定时刻的特征快照
    FeatureVector getFeatures(const DpiLog& log);

    // 统计
    size_t totalBuckets() const { return store_.size(); }
    void cleanup(int maxAgeMinutes = 10080); // 默认保留7天

private:
    // 存储: "user:user_0019:login:20260609_1432" → TimeBucket
    std::unordered_map<std::string, TimeBucket> store_;

    // 分钟Key
    static std::string minuteKey(int64_t ts);
    static std::vector<std::string> windowKeys(int64_t ts, int windowSeconds);
    int64_t currentMinute() const;

    // 聚合
    TimeBucket mergeBuckets(const std::vector<std::string>& keys);
    int countSum(const std::vector<std::string>& keys);
    int failedSum(const std::vector<std::string>& keys);
};

} // namespace zengkuai
