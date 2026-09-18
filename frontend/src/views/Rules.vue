<template>
  <div>
    <PageHeader title="规则管理">
      <template #extra>
        <el-button type="primary" size="small" :icon="Plus" @click="openAdd">新增规则</el-button>
      </template>
    </PageHeader>

    <el-card>
      <el-input v-model="search" placeholder="搜索规则名" size="small" clearable class="search-box" />
      <el-table :data="filtered" size="small" max-height="480" stripe>
        <el-table-column prop="name" label="规则名" width="160" />
        <el-table-column label="涉诈类型" width="100"><template #default="{ row }">{{ (row && row.fraudType) || '-' }}</template></el-table-column>
        <el-table-column label="等级" width="64">
          <template #default="{ row }">
            <el-tag v-if="row && row.riskLevel" :type="row.riskLevel >= 3 ? 'danger' : row.riskLevel >= 2 ? 'warning' : 'info'" size="small">{{ row.riskLevel >= 3 ? '高' : row.riskLevel >= 2 ? '中' : '低' }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="窗口" width="72"><template #default="{ row }">{{ (row && row.timeWindow) ? row.timeWindow + 'min' : '-' }}</template></el-table-column>
        <el-table-column label="特征 / 条件" width="230"><template #default="{ row }"><span v-if="row && row.conditionJson">{{ formatConds(row.conditionJson) }}</span><span v-else>{{ (row && row.feature) || '-' }}</span></template></el-table-column>
        <el-table-column label="阈值" width="70"><template #default="{ row }">{{ (row && row.conditionJson) ? '-' : (row && row.threshold) }}</template></el-table-column>
        <el-table-column prop="weight" label="权重" width="64" />
        <el-table-column prop="description" label="描述" min-width="170" />
        <el-table-column label="状态" width="70"><template #default="{ row }"><el-tag :type="row && row.enabled ? 'success' : 'info'" size="small">{{ row && row.enabled ? '启用' : '禁用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" :icon="Power" @click="toggle(row)">{{ row && row.enabled ? '禁用' : '启用' }}</el-button>
            <el-button size="small" type="danger" :icon="Trash2" @click="del(row && row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showAdd" title="新增规则" width="560px">
      <el-form :model="form" label-width="90px" size="small">
        <el-form-item label="规则名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="涉诈类型"><el-input v-model="form.fraudType" placeholder="可空，对齐 16 类诈骗类型" /></el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="form.riskLevel" placeholder="可空" clearable style="width:100%">
            <el-option label="低风险" :value="1" /><el-option label="中风险" :value="2" /><el-option label="高风险" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间窗口"><el-input-number v-model="form.timeWindow" :min="0" placeholder="分钟" /></el-form-item>
        <el-form-item label="特征字段"><el-input v-model="form.feature" placeholder="单特征规则必填" /></el-form-item>
        <el-form-item label="阈值"><el-input-number v-model="form.threshold" /></el-form-item>
        <el-form-item label="权重"><el-input-number v-model="form.weight" :min="0" :step="0.1" /></el-form-item>
        <el-form-item label="复合条件"><el-input v-model="form.conditionJson" type="textarea" :rows="2" placeholder='可选 JSON：{"conditions":[{"feature":"..","threshold":..}]}' /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" :icon="Check" @click="add">保存</el-button><el-button :icon="X" @click="showAdd = false">取消</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../stores/app'
import PageHeader from '../components/PageHeader.vue'
import { Plus, Power, Trash2, Check, X } from '@lucide/vue'

const store = useAppStore()
const rules = ref([])
const search = ref('')
const showAdd = ref(false)
const form = ref({ name: '', fraudType: '', riskLevel: null, timeWindow: null, feature: '', threshold: 0, weight: 1, conditionJson: '', description: '' })
const filtered = computed(() => search.value ? rules.value.filter((r) => r && r.name && r.name.includes(search.value)) : rules.value)

function formatConds(cj) {
  if (!cj) return '-'
  try {
    const conds = JSON.parse(cj).conditions || []
    return conds.map((c) => `${c.feature}≥${c.threshold}`).join(' & ')
  } catch (e) { return cj }
}

async function load() { const { data } = await axios.get('/api/rules'); rules.value = data }
function openAdd() { form.value = { name: '', fraudType: '', riskLevel: null, timeWindow: null, feature: '', threshold: 0, weight: 1, conditionJson: '', description: '' }; showAdd.value = true }
async function add() {
  const body = { ...form.value }
  if (!body.conditionJson) body.conditionJson = null
  await axios.post('/api/rules', body); showAdd.value = false; ElMessage.success('已添加'); load()
}
async function toggle(r) { if (!r || !r.id) return; await axios.post(`/api/rules/${r.id}/toggle`); load() }
async function del(id) { if (!id) return; await axios.delete(`/api/rules/${id}`); ElMessage.success('已删除'); load() }
onMounted(() => { load(); store.startPolling() })
onUnmounted(() => {})
</script>

<style scoped>
.search-box { width: 200px; margin-bottom: var(--space-2); }
</style>
