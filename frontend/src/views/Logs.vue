<template>
  <div>
    <PageHeader title="日志查询" />

    <el-card class="filter-card">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="用户"><el-input v-model="f.userId" placeholder="用户名" clearable @clear="search" @input="debounceSearch" style="width:160px" /></el-form-item>
        <el-form-item label="IP"><el-input v-model="f.srcIp" placeholder="IP地址" clearable @clear="search" @input="debounceSearch" style="width:170px" /></el-form-item>
        <el-form-item label="事件"><el-select v-model="f.eventType" placeholder="全部" clearable @visible-change="v => { if (!v) search() }" style="width:140px"><el-option v-for="e in eventOptions" :key="e" :label="e" :value="e" /></el-select></el-form-item>
        <el-form-item label="风险"><el-select v-model="f.riskLevel" placeholder="全部" clearable @visible-change="v => { if (!v) search() }" style="width:140px"><el-option label="关注及以上" :value="1" /><el-option label="高危及以上" :value="2" /><el-option label="紧急" :value="3" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" :icon="Search" @click="search">查询</el-button></el-form-item>
        <el-form-item><el-button :icon="RefreshCw" @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table :data="rows" size="small" max-height="520" stripe border>
        <el-table-column prop="timestamp" label="时间" width="170" />
        <el-table-column prop="userId" label="用户" width="120" />
        <el-table-column prop="srcIp" label="IP" width="150" />
        <el-table-column prop="eventType" label="事件" width="110" />
        <el-table-column label="域名" width="140"><template #default="{ row }">{{ (row && row.domain) || '-' }}</template></el-table-column>
        <el-table-column label="涉诈分类" width="110"><template #default="{ row }">{{ (row && row.fraudUrlCategory) || '-' }}</template></el-table-column>
        <el-table-column label="风险分" width="90"><template #default="{ row }"><RiskScore :score="row && row.riskScore" /></template></el-table-column>
        <el-table-column label="ML分" width="80"><template #default="{ row }">{{ (row && row.mlScore || 0).toFixed(3) }}</template></el-table-column>
        <el-table-column label="规则分" width="80"><template #default="{ row }">{{ (row && row.ruleScore || 0).toFixed(3) }}</template></el-table-column>
        <el-table-column label="决策" width="80"><template #default="{ row }"><StatusTag :decision="row && row.decision" /></template></el-table-column>
        <el-table-column label="命中规则" min-width="200"><template #default="{ row }"><span class="hit-rules">{{ (row && row.hitRules || '').slice(0, 100) }}</span></template></el-table-column>
      </el-table>
      <div class="table-footer">
        <span class="text-muted">共 {{ total }} 条结果</span>
        <el-pagination v-model:current-page="page" :page-size="30" layout="prev,next" :total="total" @current-change="search" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import { useRoute } from 'vue-router'
import { useAppStore } from '../stores/app'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import RiskScore from '../components/RiskScore.vue'
import { Search, RefreshCw } from '@lucide/vue'

const route = useRoute()
const store = useAppStore()
const f = ref({ userId: '', srcIp: '', eventType: '', riskLevel: '' })
const rows = ref([])
const page = ref(1)
const total = ref(0)
const eventOptions = ['login', 'visit_fraud_url', 'visit_suspicious_domain', 'download_app', 'fraud_call', 'fraud_sms', 'abnormal_overseas', 'screen_share', 'visit_bank_app', 'visit_meeting_app', 'modify_password', 'new_device_login', 'text_detect']

async function search(p) {
  if (typeof p === 'number') page.value = p
  else page.value = 1
  const params = { page: page.value, pageSize: 30 }
  const v = f.value
  if (v.userId) params.userId = v.userId
  if (v.srcIp) params.srcIp = v.srcIp
  if (v.eventType) params.eventType = v.eventType
  if (v.riskLevel) params.riskLevel = Number(v.riskLevel)
  const { data } = await axios.get('/api/logs', { params })
  rows.value = Array.isArray(data.content) ? data.content : (Array.isArray(data) ? data : [])
  total.value = data.totalElements || 0
}
function debounceSearch() { clearTimeout(window._lt); window._lt = setTimeout(() => search(1), 400) }
function reset() { f.value = { userId: '', srcIp: '', eventType: '', riskLevel: '' }; search(1) }
onMounted(() => {
  if (route.query.userId) f.value.userId = route.query.userId
  if (route.query.srcIp) f.value.srcIp = route.query.srcIp
  store.startPolling()
  search(1)
})
onUnmounted(() => {})
</script>

<style scoped>
.filter-card { margin-bottom: var(--space-3); }
.table-footer { display: flex; justify-content: space-between; align-items: center; margin-top: var(--space-3); }
.hit-rules { font-size: 12px; white-space: pre-line; color: var(--color-warning); }
</style>
