<template>
  <div>
    <PageHeader title="总览仪表盘" />

    <!-- 实时告警滚动条 -->
    <div v-if="store.alerts.length" class="ticker">
      <div class="ticker-track">
        <div v-for="g in 2" :key="g" class="ticker-group">
          <span v-for="a in store.alerts" :key="g + a.alertId" class="ticker-item">
            <BellRing :size="12" class="ticker-icon" />
            <b>{{ a.userId }}</b>
            <span>{{ a.title || '风险告警' }}</span>
          </span>
        </div>
      </div>
    </div>

    <!-- KPI 指标卡 -->
    <el-row :gutter="12" class="stat-row">
      <el-col v-if="!ready" :span="24">
        <el-skeleton :rows="1" animated />
      </el-col>
      <el-col v-else :span="4" v-for="s in statCards" :key="s.label">
        <StatCard :label="s.label" :value="s.value" :color="s.color" :icon="s.icon" :icon-color="s.iconColor" />
      </el-col>
    </el-row>

    <!-- 监控图表 -->
    <el-row :gutter="12" class="chart-row">
      <el-col :span="16">
        <div class="stack">
          <ChartCard title="系统实时监控 — 流量 / 告警 / 风险">
            <div id="ch1" style="height: 280px" />
          </ChartCard>
          <ChartCard title="处理延迟">
            <div id="ch3" style="height: 120px" />
          </ChartCard>
        </div>
      </el-col>
      <el-col :span="8">
        <ChartCard title="决策分布">
          <div id="ch2" style="height: 340px" />
        </ChartCard>
      </el-col>
    </el-row>

    <!-- 实时日志 + 风险告警 -->
    <el-row :gutter="12">
      <el-col :span="16">
        <ChartCard title="实时日志流">
          <template #extra>
            <div class="legend">
              <span class="legend-item"><i class="dot" style="background: var(--chart-3)" />阻断 {{ dcCounts.b }}</span>
              <span class="legend-item"><i class="dot" style="background: var(--chart-2)" />预警 {{ dcCounts.w }}</span>
              <span class="legend-item"><i class="dot" style="background: var(--chart-4)" />放行 {{ dcCounts.a }}</span>
              <el-switch v-model="store.paused" size="small" active-text="暂停" />
            </div>
          </template>
          <el-table :data="store.logs" size="small" max-height="340" stripe :row-class-name="rowClass" @row-click="showDetail">
            <el-table-column label="时间" width="85">
              <template #default="{ row }">{{ (row && row.timestamp || '').slice(11, 19) }}</template>
            </el-table-column>
            <el-table-column prop="userId" label="用户" width="95" />
            <el-table-column prop="eventType" label="事件" width="95" />
            <el-table-column label="风险分" width="75">
              <template #default="{ row }"><RiskScore :score="row && row.riskScore" /></template>
            </el-table-column>
            <el-table-column label="决策" width="70">
              <template #default="{ row }"><StatusTag :decision="row && row.decision" /></template>
            </el-table-column>
            <el-table-column label="命中规则" min-width="180">
              <template #default="{ row }"><span class="hit-rules">{{ (row && row.hitRules || '').slice(0, 80) }}</span></template>
            </el-table-column>
          </el-table>
        </ChartCard>
      </el-col>

      <el-col :span="8">
        <ChartCard title="风险告警" class="alerts-card">
          <template #extra>
            <router-link to="/alerts?from=home" class="more-link"><ArrowRight :size="14" /></router-link>
          </template>
          <EmptyState v-if="!store.alerts.length" text="暂无告警" icon="Bell" />
          <TransitionGroup name="alert" tag="div">
            <div v-for="a in store.alerts" :key="a.alertId" class="alert-item">
              <div class="alert-top">
                <div class="alert-user">
                  <b class="alert-name" @click="openAlert(a)">{{ a.userId }}</b>
                  <span class="alert-title">{{ a.title || '' }}</span>
                </div>
                <el-tag :type="a.alertLevel >= 3 ? 'danger' : 'warning'" size="small">{{ a.alertLevel >= 3 ? '紧急' : '高危' }}</el-tag>
              </div>
              <div class="alert-bottom">
                <div class="alert-detail">
                  <RiskScore :score="a.riskScore" class="mono" />
                  <span class="alert-desc">{{ (a.detail || '').slice(0, 40) }}</span>
                </div>
                <div class="alert-actions">
                  <el-button size="small" type="danger" :icon="Ban" @click.stop="quickBan(a)">封号</el-button>
                  <el-button size="small" type="warning" :icon="ShieldAlert" @click.stop="quickIntercept(a)">拦截</el-button>
                  <el-button size="small" :icon="Eye" @click.stop="openAlert(a)">详情</el-button>
                </div>
              </div>
            </div>
          </TransitionGroup>
        </ChartCard>
      </el-col>
    </el-row>

    <!-- 日志详情弹窗 -->
    <el-dialog v-model="dlg" :title="dlgRow ? dlgRow.userId + ' - 日志详情' : ''" width="720px">
      <div v-if="dlgRow">
        <div class="detail-meta">
          <span>事件: <b>{{ dlgRow.eventType }}</b></span>
          <span>IP: {{ dlgRow.srcIp }}</span>
          <span v-if="dlgRow.domain">域名: {{ dlgRow.domain }}</span>
          <span v-if="dlgRow.fraudType">类型: {{ dlgRow.fraudType }}</span>
        </div>
        <div class="detail-score">
          <div class="big-score" :style="{ color: riskColor(dlgRow.riskScore) }">{{ (dlgRow.riskScore || 0).toFixed(2) }}</div>
          <StatusTag :decision="dlgRow.decision" size="large" />
        </div>
        <div class="score-formula">
          <div class="formula-label">风险分 = 规则分×0.4 + ML分×0.6</div>
          <div class="formula mono">
            <span>{{ (dlgRow.riskScore || 0).toFixed(2) }}</span>
            <span class="op">=</span>
            <span class="rule">0.4×{{ (dlgRow.ruleScore || 0).toFixed(3) }}</span>
            <span class="op">+</span>
            <span class="ml">0.6×{{ (dlgRow.mlScore || 0).toFixed(3) }}</span>
          </div>
        </div>
        <el-row :gutter="12" class="detail-split">
          <el-col :span="12">
            <div class="split-title warn">规则命中</div>
            <div v-if="parsedRules.length" class="rule-list">
              <div v-for="(r, i) in parsedRules" :key="i" class="rule-item">
                <div class="rule-name">{{ r.name }}</div>
                <div class="rule-detail">{{ r.detail }}</div>
              </div>
            </div>
            <div v-else class="split-empty">无规则命中</div>
          </el-col>
          <el-col :span="12">
            <div class="split-title ml">ML 模型</div>
            <div class="ml-box">
              <div class="ml-line">ML模型评分: {{ (dlgRow.mlScore || 0).toFixed(4) }}</div>
              <div v-if="(dlgRow.mlScore || 0) >= 0.5" class="ml-level high">高风险</div>
              <div v-else-if="(dlgRow.mlScore || 0) >= 0.3" class="ml-level mid">中风险</div>
              <div v-else class="ml-level low">低风险</div>
            </div>
          </el-col>
        </el-row>
        <div class="summary" :style="{ background: summaryBg }">
          <b>综合分析：</b>{{ summary }}
        </div>
      </div>
      <template #footer>
        <el-button v-if="!isBanned" type="danger" :icon="Ban" @click="doBan">封号</el-button>
        <el-button v-if="!isBanned" type="warning" :icon="ShieldAlert" @click="doIntercept">拦截本次</el-button>
        <el-button v-if="isBanned" :icon="RotateCcw" @click="doUnban">解封</el-button>
        <el-button :icon="X" @click="dlg = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  Database, BellRing, ShieldAlert, Flame, Ban, CalendarClock, ArrowRight, Eye, RotateCcw, X,
} from '@lucide/vue'
import { useAppStore } from '../stores/app'
import { riskColor } from '../utils/risk'
import { CHART_COLORS, categoryAxis, valueAxis, baseTooltip, smoothAnimation } from '../utils/charts'
import PageHeader from '../components/PageHeader.vue'
import StatCard from '../components/StatCard.vue'
import StatusTag from '../components/StatusTag.vue'
import RiskScore from '../components/RiskScore.vue'
import ChartCard from '../components/ChartCard.vue'
import EmptyState from '../components/EmptyState.vue'

const store = useAppStore()
const dlg = ref(false)
const dlgRow = ref(null)
const af = ref({ '1h': 0, '24h': 0, '7d': 0 })
let c1, c2, c3
const ready = ref(false)
// 首次加载骨架屏：拿到第一条统计后即渲染真实内容（兜底 1.5s 强制就绪）
watch(() => store.stats.totalLogs, (v) => { if (v) ready.value = true }, { immediate: true })

// 行样式：block 红底、warn 橙底
function rowClass({ row }) {
  if (!row) return ''
  if (row.decision === 'block') return 'row-block'
  if (row.decision === 'warn') return 'row-warn'
  return ''
}

// 三种决策计数
const dcCounts = computed(() => {
  let a = 0, w = 0, b = 0
  store.logs.forEach((l) => {
    if (l.decision === 'block') b++
    else if (l.decision === 'warn') w++
    else a++
  })
  return { a, w, b }
})

async function fetchAF() {
  try {
    const data = await fetch('/api/alerts/frequency').then((r) => r.json())
    if (data) af.value = data
  } catch (e) {}
}

const parsedRules = computed(() => {
  const txt = (dlgRow.value && dlgRow.value.hitRules) || ''
  if (!txt.trim()) return []
  return txt.split('\n').filter((l) => l.trim()).map((l) => {
    const m = l.match(/触发「(.+?)」实际=(.+)/)
    return m ? { name: m[1], detail: '实际值: ' + m[2] } : { name: l, detail: '' }
  })
})

const summary = computed(() => {
  const r = dlgRow.value
  if (!r) return ''
  const s = r.riskScore || 0
  if (s >= 0.65) return `高风险(${s.toFixed(2)})，触发阻断。命中${parsedRules.value.length}条规则。`
  if (s >= 0.45) return `中风险(${s.toFixed(2)})，触发预警。命中${parsedRules.value.length}条规则。`
  return `低风险(${s.toFixed(2)})，正常放行。`
})

const summaryBg = computed(() => {
  const s = (dlgRow.value && dlgRow.value.riskScore) || 0
  if (s >= 0.65) return '#fdecec'
  if (s >= 0.45) return '#fdf3e6'
  return '#e7f6ec'
})

const isBanned = computed(() => {
  const uid = dlgRow.value?.userId
  return uid && (store.lists.banUsers || []).includes(uid)
})

const statCards = computed(() => [
  { label: '总日志', value: store.stats.totalLogs || 0, icon: Database, iconColor: '#2563eb' },
  { label: '告警(1h)', value: af.value['1h'] || 0, icon: BellRing, iconColor: '#d97706' },
  { label: '欺诈', value: store.stats.fraudCount || 0, icon: ShieldAlert, iconColor: '#dc2626' },
  { label: '高风险', value: store.stats.highRisk || 0, icon: Flame, color: '#dc2626', iconColor: '#dc2626' },
  { label: '已拦截', value: store.stats.blocked || 0, icon: Ban, color: '#d97706', iconColor: '#d97706' },
  { label: '告警(24h)', value: af.value['24h'] || 0, icon: CalendarClock, iconColor: '#2563eb' },
])

function showDetail(r) {
  dlgRow.value = r
  dlg.value = true
}
async function openAlert(a) {
  if (a && a.logId) {
    try {
      const res = await fetch('/api/logs/' + a.logId)
      const data = await res.json()
      dlgRow.value = data
    } catch (e) { dlgRow.value = a }
  } else {
    dlgRow.value = a
  }
  dlg.value = true
}
async function doBan() {
  const u = dlgRow.value?.userId
  if (!u) return
  await store.banUser(u)
  ElMessage.success('已封号 — 禁止访问和登录')
  dlg.value = false
}
async function doUnban() {
  const u = dlgRow.value?.userId
  if (!u) return
  await store.unbanUser(u)
  ElMessage.success('已解封 — 恢复所有操作')
  dlg.value = false
}
async function doIntercept() {
  const u = dlgRow.value?.userId
  if (!u) return
  await store.interceptUser(u)
  ElMessage.success('已拦截')
  dlg.value = false
}
async function quickBan(a) {
  await store.banUser(a.userId)
  store.removeAlert(a.alertId)
  ElMessage.success('已封号 ' + a.userId)
}
async function quickIntercept(a) {
  await store.interceptUser(a.userId)
  store.removeAlert(a.alertId)
  ElMessage.success('已拦截 ' + a.userId)
}

function draw() {
  if (!c1 || !c3 || c1.isDisposed() || c3.isDisposed()) return
  const d = store.perfHistory.slice(-60)
  const t = d.length ? d.map((r) => r[0]) : [new Date().toLocaleTimeString().slice(0, 5)]
  const tr = d.length ? d.map((r) => r[1] || 0) : [0]
  const al = d.length ? d.map((r) => r[2] || 0) : [0]
  const la = d.length ? d.map((r) => r[3] || 0) : [0]
  const ri = d.length ? d.map((r) => r[4] || 0) : [0]

  // 上图：流量 + 告警 + 风险
  c1.setOption({
    ...smoothAnimation,
    color: CHART_COLORS,
    tooltip: { ...baseTooltip, trigger: 'axis' },
    legend: { data: ['流量', '告警', '风险'], textStyle: { fontSize: 12, color: '#6b7280' }, top: 0 },
    grid: { top: 32, right: 16, bottom: 8, left: 44 },
    xAxis: { ...categoryAxis, data: t, axisLabel: { ...categoryAxis.axisLabel, interval: 9 } },
    yAxis: { ...valueAxis(), min: 0 },
    series: [
      { name: '流量', type: 'line', data: tr, smooth: true, symbol: 'none', lineStyle: { width: 2 } },
      { name: '告警', type: 'line', data: al, smooth: true, symbol: 'none', lineStyle: { width: 2 } },
      { name: '风险', type: 'line', data: ri, smooth: true, symbol: 'none', lineStyle: { width: 2 } },
    ],
  })

  // 下图：延迟
  c3.setOption({
    ...smoothAnimation,
    tooltip: { ...baseTooltip, trigger: 'axis' },
    grid: { top: 8, right: 16, bottom: 8, left: 44 },
    xAxis: { ...categoryAxis, data: t, axisLabel: { ...categoryAxis.axisLabel, interval: 9 } },
    yAxis: { ...valueAxis('ms'), min: 0 },
    series: [{
      name: '延迟', type: 'line', data: la, smooth: true, symbol: 'none',
      lineStyle: { color: CHART_COLORS[2], width: 2 },
      areaStyle: { color: 'rgba(239, 68, 68, .1)' },
    }],
  })

  // 决策分布饼图：真实三态「放行 / 预警 / 阻断」
  const dd = store.stats.decisionDistribution || {}
  const allow = Math.max(0, dd.allow || 0)
  const warn = Math.max(0, dd.warn || 0)
  const block = Math.max(0, dd.block || 0)
  if (c2 && !c2.isDisposed()) {
    c2.setOption({
      ...smoothAnimation,
      tooltip: { ...baseTooltip, trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      series: [{
        type: 'pie', radius: ['55%', '80%'],
        label: { formatter: '{b}\n{d}%', fontSize: 12, color: '#6b7280' },
        data: [
          { name: '放行', value: allow, itemStyle: { color: CHART_COLORS[3] } },
          { name: '预警', value: warn, itemStyle: { color: CHART_COLORS[1] } },
          { name: '阻断', value: block, itemStyle: { color: CHART_COLORS[2] } },
        ],
      }],
    })
  }
}

let tAF, tDraw, tInit
function onShowLogDetail(e) { showDetail(e.detail) }
onMounted(() => {
  store.startPolling()
  fetchAF()
  setTimeout(() => (ready.value = true), 1500)
  tAF = setInterval(fetchAF, 10000)
  window.addEventListener('show-log-detail', onShowLogDetail)
  tInit = setTimeout(() => {
    const d1 = document.getElementById('ch1')
    const d2 = document.getElementById('ch2')
    const d3 = document.getElementById('ch3')
    if (d1) c1 = echarts.init(d1)
    if (d2) c2 = echarts.init(d2)
    if (d3) c3 = echarts.init(d3)
    draw()
    tDraw = setInterval(draw, 3000)
  }, 800)
})
onUnmounted(() => {
  clearInterval(tAF)
  clearTimeout(tInit)
  clearInterval(tDraw)
  window.removeEventListener('show-log-detail', onShowLogDetail)
  if (c1 && !c1.isDisposed()) c1.dispose()
  if (c2 && !c2.isDisposed()) c2.dispose()
  if (c3 && !c3.isDisposed()) c3.dispose()
})
</script>

<style scoped>
.stat-row { margin-bottom: var(--space-4); }
.chart-row { margin-bottom: var(--space-4); }
.stack { display: flex; flex-direction: column; gap: var(--space-3); }

/* 实时告警滚动条 */
.ticker {
  overflow: hidden;
  border: 1px solid var(--border-1);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  padding: 8px 0;
  margin-bottom: var(--space-4);
}
.ticker-track {
  display: flex;
  width: max-content;
  animation: marquee 24s linear infinite;
}
.ticker-track:hover { animation-play-state: paused; }
.ticker-group {
  display: flex;
  gap: var(--space-6);
  padding-right: var(--space-6);
}
.ticker-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-2);
  white-space: nowrap;
}
.ticker-item b { color: var(--color-danger); }
.ticker-icon { color: var(--color-warning); }

/* 告警列表平滑过渡（新告警从顶部滑入，移除时右滑淡出） */
.alert-enter-active { transition: all var(--dur-slow) var(--ease-out); }
.alert-leave-active { transition: all var(--dur) var(--ease); }
.alert-enter-from { opacity: 0; transform: translateY(-12px); }
.alert-leave-to { opacity: 0; transform: translateX(24px); }
.alert-move { transition: transform var(--dur) var(--ease); }

.legend { display: flex; gap: var(--space-4); align-items: center; }
.legend-item { font-size: 12px; color: var(--text-3); display: inline-flex; align-items: center; gap: 4px; }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }

.hit-rules { font-size: 12px; white-space: pre-line; color: var(--color-warning); }

.more-link { color: var(--text-3); text-decoration: none; font-size: 14px; }
.alerts-card :deep(.el-card__body) { overflow-y: auto; max-height: 420px; }

.alert-item { padding: var(--space-3); border: 1px solid var(--border-1); border-radius: var(--radius-sm); margin-bottom: var(--space-2); background: var(--bg-card); }
.alert-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--space-2); }
.alert-user { display: flex; align-items: baseline; gap: var(--space-2); }
.alert-name { font-size: 13px; cursor: pointer; }
.alert-title { font-size: 12px; color: var(--text-3); }
.alert-bottom { display: flex; justify-content: space-between; align-items: center; }
.alert-detail { display: flex; align-items: center; gap: var(--space-2); }
.alert-desc { font-size: 12px; color: var(--text-3); }
.alert-actions { display: flex; gap: 4px; }

.detail-meta { display: flex; gap: var(--space-5); font-size: 12px; color: var(--text-3); margin-bottom: var(--space-4); }
.detail-score { display: flex; align-items: center; gap: var(--space-4); margin-bottom: var(--space-5); }
.big-score { font-size: 48px; font-weight: 800; line-height: 1; }
.score-formula { background: var(--bg-hover); border-radius: var(--radius-md); padding: var(--space-3) var(--space-4); margin-bottom: var(--space-4); }
.formula-label { font-size: 12px; color: var(--text-3); margin-bottom: var(--space-2); }
.formula { font-size: 13px; display: flex; align-items: center; gap: var(--space-3); }
.formula .op { color: var(--text-4); }
.formula .rule { color: var(--color-warning); }
.formula .ml { color: var(--color-primary); }
.detail-split { margin-bottom: var(--space-4); }
.split-title { font-weight: 600; font-size: 13px; margin-bottom: var(--space-2); }
.split-title.warn { color: var(--color-warning); }
.split-title.ml { color: var(--color-primary); }
.rule-list { max-height: 180px; overflow-y: auto; }
.rule-item { background: var(--el-color-warning-light-9); border-left: 3px solid var(--color-warning); padding: 6px 10px; margin-bottom: 6px; border-radius: var(--radius-sm); font-size: 12px; }
.rule-name { font-weight: 600; }
.rule-detail { color: var(--text-3); margin-top: 2px; }
.ml-box { background: var(--el-color-primary-light-9); border-radius: var(--radius-sm); padding: var(--space-3); }
.ml-line { font-size: 12px; color: var(--text-3); margin-bottom: var(--space-1); }
.ml-level { font-size: 12px; font-weight: 600; }
.ml-level.high { color: var(--color-danger); }
.ml-level.mid { color: var(--color-warning); }
.ml-level.low { color: var(--color-success); }
.split-empty { font-size: 12px; color: var(--text-3); text-align: center; padding: var(--space-5); }
.summary { border-radius: var(--radius-md); padding: var(--space-3) var(--space-4); font-size: 13px; }

:deep(.row-block) { background: var(--el-color-danger-light-9) !important; }
:deep(.row-warn) { background: var(--el-color-warning-light-9) !important; }
:deep(.row-block:hover > td) { background: #fbdada !important; }
:deep(.row-warn:hover > td) { background: #fae6c8 !important; }
</style>
