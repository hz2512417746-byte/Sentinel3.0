<template>
  <div>
    <PageHeader title="拦截历史" :back-to="fromPage ? '/' + fromPage : ''">
      <template #extra>
        <el-radio-group v-model="range" size="small" @change="load">
          <el-radio-button value="week">本周</el-radio-button>
          <el-radio-button value="month">本月</el-radio-button>
          <el-radio-button value="year">全年</el-radio-button>
        </el-radio-group>
      </template>
    </PageHeader>

    <el-card>
      <el-form :inline="true" size="small" class="filter-form">
        <el-form-item label="用户"><el-input v-model="f.userId" placeholder="用户名" clearable style="width:140px" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="f.action" placeholder="全部" clearable style="width:100px"><el-option label="封号" value="ban" /><el-option label="拦截" value="intercept" /></el-select></el-form-item>
        <el-form-item label="原因"><el-input v-model="f.reason" placeholder="原因关键词" clearable style="width:160px" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="f.active" placeholder="全部" clearable style="width:100px"><el-option label="生效中" :value="true" /><el-option label="已解除" :value="false" /></el-select></el-form-item>
        <el-form-item><el-button :icon="RefreshCw" @click="f = { userId: '', action: '', reason: '', active: '' }">重置</el-button></el-form-item>
      </el-form>
      <el-table :data="filtered" size="small" max-height="480" stripe border>
        <el-table-column label="用户" width="120"><template #default="{ row }">{{ row && row.userId || '' }}</template></el-table-column>
        <el-table-column label="类型" width="80"><template #default="{ row }"><el-tag :type="(row && row.action) === 'ban' ? 'danger' : 'warning'" size="small">{{ (row && row.action) === 'ban' ? '封号' : '拦截' }}</el-tag></template></el-table-column>
        <el-table-column label="时间" width="170"><template #default="{ row }">{{ (row && row.time || '').slice(0, 19) }}</template></el-table-column>
        <el-table-column label="原因" width="200"><template #default="{ row }">{{ row && row.reason || '' }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row && row.active ? 'danger' : 'info'" size="small">{{ row && row.active ? '生效中' : '已解除' }}</el-tag></template></el-table-column>
      </el-table>
      <div class="table-footer text-muted">共 {{ filtered.length }} 条(总数 {{ total }}) · 5秒刷新</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { useAppStore } from '../stores/app'
import PageHeader from '../components/PageHeader.vue'
import { RefreshCw } from '@lucide/vue'

const route = useRoute()
const store = useAppStore()
const fromPage = ref(route.query.from)
const range = ref('week')
const records = ref([])
const total = ref(0)
const f = ref({ userId: '', action: '', reason: '', active: '' })
let timer

const filtered = computed(() => {
  let r = records.value
  if (f.value.userId) r = r.filter((x) => x && x.userId && x.userId.includes(f.value.userId))
  if (f.value.action) r = r.filter((x) => x && x.action === f.value.action)
  if (f.value.reason) r = r.filter((x) => x && x.reason && x.reason.includes(f.value.reason))
  if (f.value.active !== '') r = r.filter((x) => x && x.active === f.value.active)
  return r
})

async function load() {
  const { data } = await axios.get(`/api/lists/history?range=${range.value}`)
  records.value = (data.records || []).filter((r) => r && r.userId)
  total.value = records.value.length
}
onMounted(() => { load(); store.startPolling(); timer = setInterval(load, 5000) })
onUnmounted(() => { clearInterval(timer) })
</script>

<style scoped>
.filter-form { margin-bottom: var(--space-2); }
.table-footer { text-align: center; margin-top: var(--space-2); font-size: 12px; }
</style>
