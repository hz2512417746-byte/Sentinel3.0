<template>
  <div>
    <PageHeader title="文本识别">
      粘贴可疑文本（诈骗短信 / 聊天记录 / 话术 / 网址），AI 识别反诈类型并归类为一条日志
    </PageHeader>

    <el-row :gutter="12">
      <el-col :span="14">
        <el-card>
          <template #header>输入文本</template>
          <el-input
            v-model="text"
            type="textarea"
            :rows="9"
            placeholder="例如：您好，我是公安局的，您的账户涉嫌洗钱，请将资金转到安全账户配合调查……"
          />
          <div class="actions">
            <el-button type="primary" :loading="loading" :icon="ScanText" @click="recognize">识别</el-button>
            <el-button :icon="Eraser" @click="reset">清空</el-button>
            <!-- 图片伏笔：后续接入 OCR 图片识别 -->
            <el-button disabled>上传图片识别（即将上线）</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card>
          <template #header>识别结果</template>
          <EmptyState v-if="!result" text="等待识别" icon="ChatDotRound" />
          <div v-else class="result">
            <div class="result-type">
              <span class="label">诈骗类型</span>
              <el-tag :type="result.fraudType === '正常' ? 'success' : 'danger'" size="large">{{ result.fraudType }}</el-tag>
            </div>
            <div class="result-row"><span class="label">置信度</span><b>{{ (result.confidence * 100).toFixed(1) }}%</b></div>
            <div class="result-row"><span class="label">处置</span><StatusTag :decision="result.decision" /></div>
            <div class="result-row"><span class="label">识别方式</span><span>{{ result.usedLlm ? 'LLM 大模型' : '关键词规则兜底' }}</span></div>
            <div class="result-reason">
              <span class="label">识别理由</span>
              <p>{{ result.reason }}</p>
            </div>
            <div class="result-row"><span class="label">日志 ID</span><span class="mono">{{ result.logId }}</span></div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import EmptyState from '../components/EmptyState.vue'
import { ScanText, Eraser } from '@lucide/vue'

const text = ref('')
const result = ref(null)
const loading = ref(false)

async function recognize() {
  if (!text.value.trim()) { ElMessage.warning('请输入要识别的文本'); return }
  loading.value = true
  try {
    const { data } = await axios.post('/api/text/recognize', { text: text.value })
    result.value = data
    if (data.fraudType === '正常') ElMessage.info('识别完成：未发现诈骗特征')
    else ElMessage.warning('识别完成：疑似「' + data.fraudType + '」')
  } catch (e) {
    ElMessage.error('识别失败：' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}
function reset() { text.value = ''; result.value = null }
</script>

<style scoped>
.actions { display: flex; gap: var(--space-3); margin-top: var(--space-3); align-items: center; }
.result { display: flex; flex-direction: column; gap: var(--space-3); }
.result-type { display: flex; align-items: center; gap: var(--space-3); }
.result-row { display: flex; align-items: center; gap: var(--space-3); font-size: 13px; }
.result-reason { font-size: 13px; }
.result-reason p { margin: 4px 0 0; font-size: 12px; color: var(--text-3); }
.label { color: var(--text-3); font-size: 13px; width: 64px; flex-shrink: 0; }
.mono { font-family: 'JetBrains Mono', Consolas, monospace; font-size: 12px; color: var(--text-2); }
</style>
