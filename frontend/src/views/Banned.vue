<template>
  <div>
    <PageHeader title="封号用户统计" :back-to="fromPage ? '/' + fromPage : ''" />

    <el-row :gutter="12" class="stat-row">
      <el-col :span="6"><StatCard label="封号总数" :value="store.lists.banUsers?.length || 0" color="#dc2626" :icon="UserX" icon-color="#dc2626" /></el-col>
      <el-col :span="6"><StatCard label="自动封禁" :value="autoBanCount" :icon="Bot" icon-color="#2563eb" /></el-col>
      <el-col :span="6"><StatCard label="手动封禁" :value="manualBanCount" :icon="Hand" icon-color="#d97706" /></el-col>
      <el-col :span="6"><StatCard label="今日新增" :value="todayBanCount" :icon="CalendarPlus" icon-color="#16a34a" /></el-col>
    </el-row>

    <el-card>
      <el-table :data="bannedUsers" size="small" max-height="450" stripe>
        <el-table-column label="用户" width="130"><template #default="{ row }">{{ row.userId }}</template></el-table-column>
        <el-table-column label="封禁时间" width="180"><template #default="{ row }">{{ row.time?.slice(0, 19) || '' }}</template></el-table-column>
        <el-table-column label="原因" min-width="200"><template #default="{ row }">{{ row.reason }}</template></el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" :icon="RotateCcw" @click="unban(row.userId)">解封</el-button>
            <el-button size="small" type="primary" :icon="ScrollText" @click="viewLogs(row.userId)">查看日志</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { useAppStore } from '../stores/app'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import StatCard from '../components/StatCard.vue'
import { UserX, Bot, Hand, CalendarPlus, RotateCcw, ScrollText } from '@lucide/vue'

const route = useRoute()
const router = useRouter()
const store = useAppStore()
const fromPage = ref(route.query.from)
const bannedUsers = ref([])
const autoBanCount = ref(0)
const manualBanCount = ref(0)
const todayBanCount = ref(0)
let timer

async function load() {
  const { data } = await axios.get('/api/lists/history?range=month')
  const all = (data.records || []).filter((r) => r && r.action === 'ban')
  bannedUsers.value = all.filter((r) => r.active)
  autoBanCount.value = all.filter((r) => (r.reason || '').includes('自动')).length
  manualBanCount.value = all.filter((r) => (r.reason || '').includes('手动')).length
  todayBanCount.value = all.filter((r) => (r.time || '').startsWith(new Date().toISOString().slice(0, 10))).length
}
async function unban(uid) { await store.unbanUser(uid); ElMessage.success('已解封'); load() }
function viewLogs(uid) { router.push('/logs?userId=' + uid) }
onMounted(() => { load(); store.startPolling(); timer = setInterval(load, 5000) })
onUnmounted(() => { clearInterval(timer) })
</script>

<style scoped>
.stat-row { margin-bottom: var(--space-4); }
</style>
