<template>
  <div>
    <PageHeader title="历史告警" :back-to="fromPage ? '/' + fromPage : ''">
      <template #extra>
        <el-radio-group v-model="range" size="small" @change="search">
          <el-radio-button value="day">今日</el-radio-button>
          <el-radio-button value="week">本周</el-radio-button>
          <el-radio-button value="month">本月</el-radio-button>
        </el-radio-group>
      </template>
    </PageHeader>

    <el-card class="filter-card">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="用户"><el-input v-model="f.userId" placeholder="用户名" clearable @clear="search" @input="debounceSearch" style="width:180px" /></el-form-item>
        <el-form-item><el-button type="primary" :icon="Search" @click="search">查询</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table :data="records" size="small" max-height="460" stripe border>
        <el-table-column label="时间" width="170"><template #default="{ row }">{{ (row && row.timestamp || '').slice(0, 19) }}</template></el-table-column>
        <el-table-column label="用户" width="110"><template #default="{ row }">{{ row && row.userId }}</template></el-table-column>
        <el-table-column label="等级" width="80"><template #default="{ row }"><el-tag :type="(row && row.alertLevel) >= 3 ? 'danger' : (row && row.alertLevel) >= 2 ? 'warning' : 'info'" size="small">{{ (row && row.alertLevel) >= 3 ? '紧急' : (row && row.alertLevel) >= 2 ? '高危' : '关注' }}</el-tag></template></el-table-column>
        <el-table-column label="风险分" width="85"><template #default="{ row }"><RiskScore :score="row && row.riskScore" /></template></el-table-column>
        <el-table-column label="详情" min-width="300"><template #default="{ row }">{{ row && row.detail || '' }}</template></el-table-column>
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
import { useRoute } from 'vue-router'
import axios from 'axios'
import { useAppStore } from '../stores/app'
import PageHeader from '../components/PageHeader.vue'
import RiskScore from '../components/RiskScore.vue'
import { Search } from '@lucide/vue'

const route = useRoute()
const store = useAppStore()
const range = ref('day')
const fromPage = ref(route.query.from)
const records = ref([])
const total = ref(0)
const page = ref(1)
const f = ref({ userId: '' })
let timer

async function search(p) {
  if (typeof p === 'number') page.value = p
  else page.value = 1
  const params = { range: range.value, page: page.value, pageSize: 30 }
  if (f.value.userId) params.userId = f.value.userId
  const { data } = await axios.get('/api/alerts/history', { params })
  records.value = Array.isArray(data.content) ? data.content : (Array.isArray(data) ? data : [])
  total.value = data.totalElements || 0
}
function debounceSearch() { clearTimeout(window._at); window._at = setTimeout(() => search(1), 400) }
onMounted(() => { store.startPolling(); search(); timer = setInterval(search, 10000) })
onUnmounted(() => { clearInterval(timer) })
</script>

<style scoped>
.filter-card { margin-bottom: var(--space-3); }
.table-footer { display: flex; justify-content: space-between; align-items: center; margin-top: var(--space-3); }
</style>
