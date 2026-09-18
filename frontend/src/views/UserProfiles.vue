<template>
  <div>
    <PageHeader title="用户画像" />

    <el-card class="filter-card">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="用户"><el-input v-model="q" placeholder="user_0001 / 手机号" clearable @clear="search" @input="debounceSearch" style="width:180px" /></el-form-item>
        <el-form-item><el-button type="primary" :icon="Search" @click="search">查询</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table :data="rows" size="small" max-height="520" stripe border @row-click="showDetail">
        <el-table-column prop="userId" label="用户" width="120" />
        <el-table-column prop="billNo" label="手机号" width="130" />
        <el-table-column label="年龄" width="60"><template #default="{ row }">{{ row && row.age }}</template></el-table-column>
        <el-table-column label="性别" width="60"><template #default="{ row }">{{ sexLabel(row && row.sex) }}</template></el-table-column>
        <el-table-column label="行业" width="90"><template #default="{ row }">{{ occLabel(row && row.occupation) }}</template></el-table-column>
        <el-table-column label="客户级别" width="90"><template #default="{ row }">{{ lvlLabel(row && row.custLvl) }}</template></el-table-column>
        <el-table-column label="在网时长(天)" width="100"><template #default="{ row }">{{ row && row.innetDur }}</template></el-table-column>
        <el-table-column prop="cityId" label="城市" width="80" />
        <el-table-column prop="termMdl" label="终端机型" min-width="130" />
      </el-table>
      <div class="table-footer">
        <span class="text-muted">共 {{ total }} 个用户画像</span>
        <el-pagination v-model:current-page="page" :page-size="pageSize" layout="prev,next" :total="total" @current-change="search" />
      </div>
    </el-card>

    <el-drawer v-model="detailShow" title="用户画像详情" size="420px">
      <el-descriptions v-if="cur" :column="1" border size="small">
        <el-descriptions-item label="用户ID">{{ cur.userId }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ cur.billNo }}</el-descriptions-item>
        <el-descriptions-item label="客户状态">{{ stateLabel(cur.custState) }}</el-descriptions-item>
        <el-descriptions-item label="客户级别">{{ lvlLabel(cur.custLvl) }}</el-descriptions-item>
        <el-descriptions-item label="年龄">{{ cur.age }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ sexLabel(cur.sex) }}</el-descriptions-item>
        <el-descriptions-item label="所属行业">{{ occLabel(cur.occupation) }}</el-descriptions-item>
        <el-descriptions-item label="婚姻状况">{{ marryLabel(cur.marryState) }}</el-descriptions-item>
        <el-descriptions-item label="实名认证">{{ realLabel(cur.realNameFlag) }}</el-descriptions-item>
        <el-descriptions-item label="城市编码">{{ cur.cityId }}</el-descriptions-item>
        <el-descriptions-item label="县区编码">{{ cur.countyId }}</el-descriptions-item>
        <el-descriptions-item label="用户状态">{{ cur.userState }}</el-descriptions-item>
        <el-descriptions-item label="信用分档">{{ cur.userCreditId }}</el-descriptions-item>
        <el-descriptions-item label="信用额度(分)">{{ cur.userCreditValue }}</el-descriptions-item>
        <el-descriptions-item label="在网时长(天)">{{ cur.innetDur }}</el-descriptions-item>
        <el-descriptions-item label="终端机型">{{ cur.termMdl }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import PageHeader from '../components/PageHeader.vue'
import { Search } from '@lucide/vue'

const q = ref('')
const rows = ref([])
const page = ref(1)
const pageSize = 20
const total = ref(0)
const detailShow = ref(false)
const cur = ref(null)

const sexLabel = (v) => (v === 1 ? '男' : v === 0 ? '女' : '-')
const lvlLabel = (v) => ({ 0: '无', 1: '钻石卡', 2: '金卡', 3: '银卡', 4: '普通' }[v] || '-')
const stateLabel = (v) => ({ 1: '潜在', 2: '在网', 3: '离网', 4: '销户' }[v] || '-')
const marryLabel = (v) => ({ 0: '未知', 1: '已婚', 2: '未婚' }[v] || '-')
const realLabel = (v) => (v === 1 || v === 11 ? '实名' : v === 0 || v === 10 ? '非实名' : '待确认')
const occLabel = (v) => ({ 1: '党政军', 2: '金融保险', 3: '交通运输', 4: '商业服务', 5: '文教科研', 6: '旅游', 7: '新闻出版', 9: '其他' }[v] || '-')

async function search(p) {
  if (typeof p === 'number') page.value = p
  else page.value = 1
  const params = { page: page.value, pageSize }
  if (q.value) params.q = q.value
  const { data } = await axios.get('/api/users', { params })
  rows.value = Array.isArray(data.content) ? data.content : (Array.isArray(data) ? data : [])
  total.value = data.totalElements || 0
}
function debounceSearch() { clearTimeout(window._ut); window._ut = setTimeout(() => search(1), 400) }
function showDetail(row) { cur.value = row; detailShow.value = true }
onMounted(() => search(1))
</script>

<style scoped>
.filter-card { margin-bottom: var(--space-3); }
.table-footer { display: flex; justify-content: space-between; align-items: center; margin-top: var(--space-3); }
</style>
