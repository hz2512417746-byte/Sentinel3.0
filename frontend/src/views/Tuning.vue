<template>
  <div>
    <PageHeader title="规则调参预览">
      <span v-if="!ready" style="color: var(--color-warning)">数据准备中...</span>
      <span v-else>总计 {{ total.toLocaleString() }} 条日志</span>
      <span v-if="store.paused" class="paused">● 已暂停</span>
    </PageHeader>

    <el-card>
      <el-table :data="rules" size="small" max-height="500" stripe>
        <el-table-column prop="name" label="规则名" width="160" />
        <el-table-column prop="feature" label="特征字段" width="200" />
        <el-table-column label="当前阈值" width="90"><template #default="{ row }">{{ row.threshold }}</template></el-table-column>
        <el-table-column label="新阈值" width="240">
          <template #default="{ row }">
            <el-slider v-model="sliders[row.id]" :min="0" :max="Math.max(row.threshold * 20 || 100000, 100000)" :step="Math.max(1, Math.floor(row.threshold / 20))" show-input size="small" class="slider" @change="(v) => onSliderChange(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="命中数" width="90">
          <template #default="{ row }"><span :style="{ color: hits[row.id] > 10 ? '#dc2626' : '#16a34a', fontWeight: '700' }">{{ hits[row.id] || 0 }}</span></template>
        </el-table-column>
        <el-table-column label="占比" width="160">
          <template #default="{ row }"><el-progress :percentage="pctg[row.id] || 0" :color="(pctg[row.id] || 0) > 10 ? '#dc2626' : '#16a34a'" :stroke-width="16" /></template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import axios from 'axios'
import { useAppStore } from '../stores/app'
import PageHeader from '../components/PageHeader.vue'

const store = useAppStore()
const rules = ref([])
const sliders = reactive({})
const hits = reactive({})
const pctg = reactive({})
const total = ref(0)
const ready = ref(false)
let running = false
let sliderTimer = null
let batchTimer = null

async function load() {
  const { data } = await axios.get('/api/rules')
  rules.value = (Array.isArray(data) ? data : []).filter((r) => r && r.enabled)
  try { const { data: s } = await axios.get('/api/stats'); total.value = s.totalLogs || 0 } catch (e) {}
  for (const r of rules.value) sliders[r.id] = r.threshold
  // 最多等 30 秒种子完成
  for (let retry = 0; retry < 30 && !ready.value; retry++) {
    const r0 = rules.value[0]
    if (!r0) break
    try { const { data } = await axios.get('/api/rules/preview', { params: { ruleName: r0.name, threshold: 0 } }); if (data.ready) { ready.value = true; break } } catch (e) {}
    await new Promise((r) => setTimeout(r, 1000))
  }
  if (!ready.value) ready.value = true // 超时也继续，显示已有数据
  await updateAll()
}

async function updateAll() {
  if (running) return
  running = true
  try { const { data: s } = await axios.get('/api/stats'); total.value = s.totalLogs || 0 } catch (e) {}
  await Promise.all(rules.value.map((r) => preview(r, sliders[r.id])))
  running = false
}

async function preview(r, v) {
  if (!r || !r.name) return
  try {
    const { data } = await axios.get('/api/rules/preview', { params: { ruleName: r.name, threshold: v } })
    if (data.ready) ready.value = true
    hits[r.id] = data.hitCount || 0
    pctg[r.id] = total.value > 0 ? Math.round((data.hitCount || 0) / total.value * 10000) / 100 : 0
  } catch (e) {}
}

// 滑块防抖——拖动停止 300ms 后才发请求
function onSliderChange(r, v) { clearTimeout(sliderTimer); sliderTimer = setTimeout(() => preview(r, v), 300) }

// 批量更新——新日志累积 200ms 后一起请求
watch(() => store.logCount, () => {
  if (store.paused) return
  clearTimeout(batchTimer)
  batchTimer = setTimeout(() => updateAll(), 200)
})

// 解暂停立刻刷新
watch(() => store.paused, (nv) => { if (!nv) updateAll() })

onMounted(() => { load(); store.startPolling() })
onUnmounted(() => { clearTimeout(sliderTimer); clearTimeout(batchTimer) })
</script>

<style scoped>
.paused { color: var(--color-warning); margin-left: var(--space-2); }
.slider { width: 200px; }
</style>
