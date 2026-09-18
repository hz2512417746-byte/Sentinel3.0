<template>
  <div>
    <PageHeader title="管线性能" />

    <div class="card-row">
      <div v-for="c in cards" :key="c.label" class="card-cell">
        <StatCard :label="c.label" :value="c.value" :color="c.color" :icon="c.icon" :icon-color="c.iconColor" />
      </div>
    </div>

    <el-row :gutter="10">
      <el-col :span="16">
        <ChartCard title="延迟趋势"><div id="pipe_ch1" style="height: 300px" /></ChartCard>
      </el-col>
      <el-col :span="8">
        <ChartCard title="耗时占比"><div id="pipe_ch2" style="height: 300px" /></ChartCard>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import * as echarts from 'echarts'
import { useAppStore } from '../stores/app'
import { CHART_COLORS, categoryAxis, valueAxis, baseTooltip, smoothAnimation } from '../utils/charts'
import { Gauge, Layers, Settings2, Cpu, GitBranch, Database, FileText } from '@lucide/vue'
import PageHeader from '../components/PageHeader.vue'
import StatCard from '../components/StatCard.vue'
import ChartCard from '../components/ChartCard.vue'

const store = useAppStore()
const sm = ref({})
const samples = ref([])
let i1, i2, timer
const s = (k) => sm.value[k] || 0

const cards = computed(() => [
  { label: '总延迟', value: s('avg_total_ms') + 'ms', color: CHART_COLORS[0], icon: Gauge, iconColor: CHART_COLORS[0] },
  { label: '增块', value: s('avg_zengkuai_ms') + 'ms', color: CHART_COLORS[4], icon: Layers, iconColor: CHART_COLORS[4] },
  { label: '规则', value: s('avg_rule_ms') + 'ms', color: CHART_COLORS[3], icon: Settings2, iconColor: CHART_COLORS[3] },
  { label: 'ML', value: s('avg_ml_ms') + 'ms', color: CHART_COLORS[1], icon: Cpu, iconColor: CHART_COLORS[1] },
  { label: '决策', value: s('avg_decide_ms') + 'ms', color: CHART_COLORS[5], icon: GitBranch, iconColor: CHART_COLORS[5] },
  { label: 'DB', value: s('avg_db_ms') + 'ms', color: CHART_COLORS[2], icon: Database, iconColor: CHART_COLORS[2] },
  { label: '日志数', value: store.logCount, color: '#6b7280', icon: FileText, iconColor: '#6b7280' },
])

async function rf() {
  try {
    const { data } = await axios.get('/api/pipeline/metrics')
    sm.value = data.stats || {}
    samples.value = data.samples || []
    draw()
  } catch (e) {}
}

function draw() {
  if (!i1 || !i2) return
  const d = samples.value.slice(-60)
  if (!d.length) return
  const t = d.map((r) => r.ts || '')
  const line = (name, key, color, dashed = false) => ({
    name, type: 'line', data: d.map((r) => r[key] || 0), smooth: !dashed, symbol: 'none',
    lineStyle: { color, width: dashed ? 1 : 2, type: dashed ? 'dashed' : 'solid' },
  })
  i1.setOption({
    ...smoothAnimation,
    color: [CHART_COLORS[4], CHART_COLORS[3], CHART_COLORS[1], CHART_COLORS[5], CHART_COLORS[2], CHART_COLORS[0]],
    tooltip: { ...baseTooltip, trigger: 'axis' },
    legend: { data: ['增块', '规则', 'ML', '决策', 'DB', '总计'], textStyle: { fontSize: 11, color: '#6b7280' }, top: 0 },
    grid: { top: 32, right: 12, bottom: 8, left: 48 },
    xAxis: { ...categoryAxis, data: t },
    yAxis: valueAxis('ms'),
    series: [
      line('增块', 'zengkuai_ms', CHART_COLORS[4]),
      line('规则', 'rule_ms', CHART_COLORS[3]),
      line('ML', 'ml_ms', CHART_COLORS[1]),
      line('决策', 'decide_ms', CHART_COLORS[5]),
      line('DB', 'db_ms', CHART_COLORS[2]),
      line('总计', 'total_ms', CHART_COLORS[0], true),
    ],
  })

  const seg = [
    { name: '增块', v: Number(s('avg_zengkuai_ms')) || 0, c: CHART_COLORS[4] },
    { name: '规则', v: Number(s('avg_rule_ms')) || 0, c: CHART_COLORS[3] },
    { name: 'ML', v: Number(s('avg_ml_ms')) || 0, c: CHART_COLORS[1] },
    { name: '决策', v: Number(s('avg_decide_ms')) || 0, c: CHART_COLORS[5] },
    { name: 'DB', v: Number(s('avg_db_ms')) || 0, c: CHART_COLORS[2] },
  ]
  i2.setOption({
    ...smoothAnimation,
    tooltip: { ...baseTooltip, trigger: 'item', formatter: '{b}: {c}ms ({d}%)' },
    series: [{
      type: 'pie', radius: ['50%', '75%'],
      label: { formatter: '{b} {d}%', fontSize: 12, color: '#6b7280' },
      data: seg.map((x) => ({ name: x.name, value: x.v, itemStyle: { color: x.c } })),
    }],
  })
}

onMounted(() => {
  store.startPolling()
  rf()
  timer = setInterval(rf, 2000)
  setTimeout(() => {
    const d1 = document.getElementById('pipe_ch1')
    if (d1) i1 = echarts.init(d1)
    const d2 = document.getElementById('pipe_ch2')
    if (d2) i2 = echarts.init(d2)
    draw()
  }, 600)
})
onUnmounted(() => {
  clearInterval(timer)
  if (i1 && !i1.isDisposed()) i1.dispose()
  if (i2 && !i2.isDisposed()) i2.dispose()
})
</script>

<style scoped>
.card-row { display: flex; gap: var(--space-2); margin-bottom: var(--space-4); }
.card-cell { flex: 1; }
</style>
