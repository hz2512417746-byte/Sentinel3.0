<template>
  <div>
    <PageHeader title="风险用户" />

    <el-row :gutter="12" class="users-row">
      <el-col :span="12">
        <el-card class="list-card" shadow="never">
          <template #header>
            <div class="card-head">
              <span>已拦截({{ filteredI.length }})</span>
              <router-link to="/history?from=users" class="more-link"><ArrowRight :size="14" /></router-link>
            </div>
          </template>
          <el-input v-model="iSearch" placeholder="搜索用户名" size="small" clearable class="search-box" />
          <el-table :data="filteredI" size="small" border>
            <el-table-column prop="userId" label="用户" width="180" />
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button size="small" type="danger" :icon="Ban" @click="store.banUser(row.userId)">封号</el-button>
                <el-button size="small" :icon="RotateCcw" @click="store.uninterceptUser(row.userId)">解除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card class="list-card" shadow="never">
          <template #header>
            <div class="card-head">
              <span>已封号({{ filteredB.length }})</span>
              <router-link to="/banned?from=users" class="more-link"><ArrowRight :size="14" /></router-link>
            </div>
          </template>
          <el-input v-model="bSearch" placeholder="搜索用户名" size="small" clearable class="search-box" />
          <el-table :data="filteredB" size="small" border>
            <el-table-column prop="userId" label="用户" width="180" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }"><el-button size="small" :icon="RotateCcw" @click="store.unbanUser(row.userId)">解封</el-button></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useAppStore } from '../stores/app'
import PageHeader from '../components/PageHeader.vue'
import { Ban, RotateCcw, ArrowRight } from '@lucide/vue'

const store = useAppStore()
const iSearch = ref('')
const bSearch = ref('')
const iUsers = computed(() => (store.lists.interceptUsers || []).map((u) => ({ userId: u })))
const bUsers = computed(() => (store.lists.banUsers || []).map((u) => ({ userId: u })))
const filteredI = computed(() => iSearch.value ? iUsers.value.filter((u) => u.userId.includes(iSearch.value)) : iUsers.value)
const filteredB = computed(() => bSearch.value ? bUsers.value.filter((u) => u.userId.includes(bSearch.value)) : bUsers.value)

onMounted(() => {
  store.startPolling()
  store.fetchLists()
  const t = setInterval(() => store.fetchLists(), 2000)
  onUnmounted(() => clearInterval(t))
})
</script>

<style scoped>
.users-row { height: 500px; }
.list-card { height: 100%; }
.list-card :deep(.el-card__body) { height: calc(100% - 96px); overflow-y: auto; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.more-link { color: var(--text-3); text-decoration: none; font-size: 15px; }
.search-box { margin-bottom: var(--space-2); }
</style>
