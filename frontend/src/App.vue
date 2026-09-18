<template>
  <div class="app-shell">
    <!-- 侧边栏：品牌 + 导航菜单 -->
    <aside class="sidebar">
      <div class="logo" @click="showArch = true">
        <span class="logo-mark">S</span>
        <span class="logo-text">Sentinel 3.0</span>
      </div>
      <nav class="menu">
        <router-link v-for="r in menuRoutes" :key="r.path" :to="r.path" class="menu-item">
          <el-icon class="menu-icon"><component :is="r.meta.icon" /></el-icon>
          <span>{{ r.meta.title }}</span>
        </router-link>
      </nav>
      <div class="sidebar-foot text-muted">DPI 实时反诈预警系统</div>
    </aside>

    <!-- 主区域 -->
    <div class="main">
      <header class="header">
        <div class="header-title">{{ currentTitle }}</div>
        <div class="header-right">
          <!-- 阻断/1min 状态：点击展开最近阻断列表 -->
          <el-popover placement="bottom-end" :width="280" trigger="click">
            <template #reference>
              <div class="block-pill" :class="{ pulse: pulsing }">
                <span class="pill-num">{{ blockCount1m }}</span>
                <span class="pill-label">阻断/1min</span>
              </div>
            </template>
            <div class="pop-title">最近阻断 <span class="text-muted">({{ recentBlocks.length }}条)</span></div>
            <div v-if="!recentBlocks.length" class="pop-empty">暂无阻断</div>
            <div v-for="b in recentBlocks" :key="b.logId" class="pop-item" @click="goLogDetail(b)">
              <div class="pop-item-top">
                <b>{{ b.userId }}</b>
                <span class="risk">{{ (b.riskScore || 0).toFixed(2) }}</span>
              </div>
              <div class="pop-item-sub">{{ b.hitRules || '无规则命中' }}</div>
            </div>
          </el-popover>
        </div>
      </header>
      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </main>
    </div>

    <!-- 系统架构弹窗（保留） -->
    <el-dialog v-model="showArch" title="Sentinel 3.0 系统架构" width="800px">
      <el-tabs>
        <el-tab-pane label="技术栈">
          <el-table :data="techStack" size="small">
            <el-table-column prop="layer" label="层级" width="100" />
            <el-table-column prop="name" label="组件" width="180" />
            <el-table-column prop="role" label="作用" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="数据流">
          <div class="flow-wrap">
<pre class="flow">
┌──────────┐
│ 模拟器    │  80条/s, 200用户, 3%欺诈率
└────┬─────┘
     │  JSON
     ▼
┌──────────────────┐
│   Kafka 3.9      │  Topic: dpi_logs
│                  │  缓冲 + 解耦
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────┐
│   Flink 1.18                 │  实时流处理引擎
│                              │
│  RichMapFunction             │
│  ├─ ZengKuaiEngine 时间分桶  │
│  ├─ 27维特征实时计算          │
│  └─ 特征注入原始消息          │
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────┐
│   Kafka 3.9      │  Topic: dpi_enriched
│                  │  带 features 的富化数据
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────────────────┐
│         Spring Boot 3.2                  │
│                                          │
│  Kafka Consumer ──→ 规则引擎             │
│       │             DPI反诈规则+特征阈值 │
│       │                    │             │
│       │         ┌─────────┴──────────┐  │
│       │         │  SMILE 逻辑回归模型 │  │
│       │         │  27维特征 → P(欺诈) │  │
│       │         └─────────┬──────────┘  │
│       │                   │             │
│       │         决策融合(规则40%+ML 60%) │
│       │                   │             │
│       │         ┌─────────┴──────────┐  │
│       │         │  三态决策           │  │
│       │         │  block/warn/allow  │  │
│       │         └─────────┬──────────┘  │
│       │                   │             │
│       ├──→ 拦截/封禁 ←───┘              │
│       │                                 │
│       ▼                    ▼            │
│   ┌──────┐          ┌──────────────┐   │
│   │Redis │          │   MySQL 8.0   │   │
│   │封号  │          │ log_events   │   │
│   │拦截  │          │ alerts       │   │
│   └──────┘          │ block_records│   │
│      ▲              └──────┬───────┘   │
│      └── 自动封禁(≥50次告警) ┘          │
└──────────────────┬─────────────────────┘
                   │ REST + 原生WebSocket
                   ▼
            ┌────────────┐
            │ Vue3 前端   │
            │ 10页仪表盘  │
            │ Pinia+ECharts│
            │ Element Plus │
            └────────────┘
</pre>
          </div>
        </el-tab-pane>
        <el-tab-pane label="前后端职责">
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="Flink">Kafka源 → ZengKuai特征计算 → Kafka汇</el-descriptions-item>
            <el-descriptions-item label="ZengKuai">时间分桶增量聚合, 单条&lt;5ms</el-descriptions-item>
            <el-descriptions-item label="SMILE">逻辑回归, 模型部署</el-descriptions-item>
            <el-descriptions-item label="Spring Boot">消费dpi_enriched → 规则引擎 + ML评分 → 三态决策 → 自动封禁 → 批量MySQL + WebSocket实时推送</el-descriptions-item>
            <el-descriptions-item label="Vue3">10页仪表盘: 实时监控+日志查询+告警历史+用户管理+用户画像+拦截/封号+规则管理+调参预览+管线性能, Pinia+ECharts+ElementPlus+WebSocket</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- AI 助手悬浮面板 -->
    <AiAssistant />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from './stores/app'
import AiAssistant from './components/AiAssistant.vue'

const router = useRouter()
const route = useRoute()
const store = useAppStore()

const showArch = ref(false)
const pulsing = ref(false)

// 侧边栏菜单：取带 title 的路由（排除兜底重定向）
const menuRoutes = router.options.routes.filter((r) => r.meta?.title)
const currentTitle = computed(() => route.meta?.title || '总览')

// 最近 1 分钟阻断数
const blockCount1m = computed(() => {
  const now = Date.now()
  let c = 0
  for (const l of store.logs) {
    if (l.decision === 'block' && l.timestamp) {
      const t = new Date(l.timestamp).getTime()
      if (now - t < 60000) c++
    }
  }
  return c
})
const recentBlocks = computed(() => store.logs.filter((l) => l.decision === 'block').slice(0, 5))

// 阻断数增长时给状态胶囊加一次脉冲动画
let lastCount = 0
watch(blockCount1m, (nv) => {
  if (nv > lastCount) {
    pulsing.value = true
    setTimeout(() => (pulsing.value = false), 400)
  }
  lastCount = nv
})

// 跳转首页并打开某条日志详情
function goLogDetail(b) {
  router.push('/').then(() => {
    setTimeout(() => window.dispatchEvent(new CustomEvent('show-log-detail', { detail: b })), 200)
  })
}

const techStack = [
  { layer: '消息队列', name: 'Kafka 3.9', role: 'dpi_logs + dpi_enriched 双topic解耦' },
  { layer: '流处理', name: 'Flink 1.18', role: 'RichMapFunction内嵌特征计算' },
  { layer: '增量特征', name: 'ZengKuai 增块引擎', role: '时间分桶增量聚合' },
  { layer: 'ML模型', name: 'SMILE 逻辑回归', role: '27维特征实时推理' },
  { layer: '规则引擎', name: '规则引擎', role: '15条通用+24条湖州复杂规则(AND+三级预警)' },
  { layer: '缓存', name: 'Redis', role: '黑名单缓存, 双向同步' },
  { layer: '持久化', name: 'MySQL 8.0', role: '日志+告警+拦截记录' },
  { layer: '后端框架', name: 'Spring Boot 3.2', role: 'REST API + WebSocket实时推送' },
  { layer: '前端', name: 'Vue3+Pinia+ElementPlus+ECharts', role: '10页仪表盘, 实时数据展示' },
]
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  background: var(--bg-page);
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: var(--bg-card);
  border-right: 1px solid var(--border-1);
  display: flex;
  flex-direction: column;
}
.logo {
  height: var(--header-height);
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-4);
  cursor: pointer;
  border-bottom: 1px solid var(--border-2);
}
.logo-mark {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, var(--color-primary), #1d4ed8);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.logo-text {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-1);
}
.menu {
  flex: 1;
  padding: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 9px var(--space-3);
  border-radius: var(--radius-sm);
  color: var(--text-2);
  text-decoration: none;
  font-size: 14px;
  transition: background .15s, color .15s;
}
.menu-item:hover {
  background: var(--bg-hover);
  color: var(--text-1);
}
.menu-item.router-link-active {
  background: var(--el-color-primary-light-9);
  color: var(--color-primary);
  font-weight: 600;
}
.menu-icon {
  font-size: 16px;
}
.sidebar-foot {
  padding: var(--space-4);
  font-size: 12px;
  border-top: 1px solid var(--border-2);
  text-align: center;
}

/* ===== 主区域 ===== */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.header {
  height: var(--header-height);
  flex-shrink: 0;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-1);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-6);
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}
.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.content {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-6);
}

/* ===== 阻断/1min 状态胶囊 ===== */
.block-pill {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 6px 14px;
  border-radius: 999px;
  background: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-8);
  cursor: pointer;
  user-select: none;
  transition: transform .2s;
}
.block-pill:hover {
  transform: scale(1.04);
}
.block-pill.pulse {
  animation: cpulse .4s ease-out;
}
@keyframes cpulse {
  0% { transform: scale(1); }
  50% { transform: scale(1.12); }
  100% { transform: scale(1); }
}
.pill-num {
  font-size: 18px;
  font-weight: 800;
  color: var(--color-danger);
  line-height: 1;
}
.pill-label {
  font-size: 12px;
  color: var(--color-danger);
}

/* ===== 最近阻断弹层 ===== */
.pop-title {
  padding: 4px 2px var(--space-2);
  font-size: 13px;
  font-weight: 700;
  border-bottom: 1px solid var(--border-2);
}
.pop-empty {
  text-align: center;
  color: var(--text-3);
  padding: var(--space-5);
  font-size: 12px;
}
.pop-item {
  padding: var(--space-2) 2px;
  border-bottom: 1px solid var(--border-2);
  cursor: pointer;
}
.pop-item:hover .pop-item-top b {
  color: var(--color-primary);
}
.pop-item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pop-item-top b {
  font-size: 12px;
  color: var(--text-1);
}
.pop-item-top .risk {
  color: var(--color-danger);
  font-weight: 700;
  font-size: 12px;
}
.pop-item-sub {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 架构弹窗数据流 ===== */
.flow-wrap {
  background: var(--bg-hover);
  border-radius: var(--radius-md);
  padding: var(--space-4);
}
.flow {
  font-family: 'JetBrains Mono', Consolas, monospace;
  line-height: 1.7;
  font-size: 12px;
  color: var(--text-2);
  overflow-x: auto;
}
</style>
