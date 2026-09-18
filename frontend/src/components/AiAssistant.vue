<template>
  <div>
    <!-- 悬浮按钮 -->
    <div class="ai-fab" :class="{ active: open }" @click="open = !open">
      <el-icon :size="20"><component :is="open ? 'Close' : 'ChatDotRound'" /></el-icon>
    </div>

    <!-- 对话面板 -->
    <transition name="slide">
      <div v-if="open" class="ai-panel">
        <div class="ai-head">
          <div class="ai-title">AI 助手</div>
          <div class="ai-actions">
            <el-icon class="ai-icon" @click="showSettings = true"><Setting /></el-icon>
            <el-icon class="ai-icon" @click="open = false"><Close /></el-icon>
          </div>
        </div>

        <!-- 快捷指令 -->
        <div class="ai-quick">
          <el-button size="small" @click="quick('summary')">预警摘要</el-button>
          <el-button size="small" @click="quick('rules')">规则解释</el-button>
          <el-button size="small" @click="quick('insight')">数据洞察</el-button>
        </div>

        <!-- 消息区 -->
        <div ref="msgBox" class="ai-messages">
          <div v-if="!messages.length && !loading" class="ai-empty">问我关于预警、规则或数据的问题</div>
          <div v-for="(m, i) in messages" :key="i" class="ai-msg" :class="m.role">
            <div class="ai-bubble">{{ m.content }}</div>
          </div>
          <div v-if="loading" class="ai-msg assistant"><div class="ai-bubble typing">思考中…</div></div>
        </div>

        <!-- 输入区 -->
        <div class="ai-input">
          <el-input v-model="draft" placeholder="输入问题，回车发送…" :disabled="loading" @keyup.enter="send" />
          <el-button type="primary" :disabled="loading || !draft.trim()" @click="send">发送</el-button>
        </div>
      </div>
    </transition>

    <!-- 设置弹窗 -->
    <el-dialog v-model="showSettings" title="AI 助手设置" width="460px" append-to-body>
      <el-form label-width="90px" size="small">
        <el-form-item label="服务商">
          <el-select v-model="settings.provider" style="width: 100%">
            <el-option v-for="(p, k) in PROVIDERS" :key="k" :label="p.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型"><el-input v-model="settings.model" placeholder="模型 ID" /></el-form-item>
        <el-form-item label="Base URL"><el-input v-model="settings.baseUrl" placeholder="留空使用默认地址" /></el-form-item>
        <el-form-item label="API Key"><el-input v-model="settings.apiKey" type="password" show-password placeholder="sk-…" /></el-form-item>
      </el-form>
      <div class="ai-note">⚠️ Key 仅保存在本地浏览器；浏览器直连云端 API 仅适合演示，生产环境请改用后端代理。</div>
      <template #footer>
        <el-button @click="showSettings = false">取消</el-button>
        <el-button type="primary" @click="saveSettings">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../stores/app'

const store = useAppStore()
const open = ref(false)
const showSettings = ref(false)
const draft = ref('')
const messages = ref([])
const loading = ref(false)
const msgBox = ref(null)

// 支持的服务商：Anthropic 与 OpenAI 兼容两种协议
const PROVIDERS = {
  anthropic: { label: 'Anthropic (Claude)', url: 'https://api.anthropic.com/v1/messages', defaultModel: 'claude-sonnet-5' },
  openai: { label: 'OpenAI 兼容', url: 'https://api.openai.com/v1/chat/completions', defaultModel: 'gpt-4o-mini' },
}

const DEFAULT = { provider: 'anthropic', model: 'claude-sonnet-5', baseUrl: '', apiKey: '' }
const settings = reactive(loadSettings())

function loadSettings() {
  try {
    return { ...DEFAULT, ...JSON.parse(localStorage.getItem('sentinel-ai-settings') || '{}') }
  } catch (e) {
    return { ...DEFAULT }
  }
}
function saveSettings() {
  localStorage.setItem('sentinel-ai-settings', JSON.stringify(settings))
  showSettings.value = false
  ElMessage.success('设置已保存')
}

// 切换服务商时自动带上对应默认模型
watch(() => settings.provider, (p) => {
  const def = PROVIDERS[p]?.defaultModel
  if (def) settings.model = def
})

function scrollBottom() {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

// 用当前 store 数据拼一段系统上下文
function context() {
  const s = store.stats
  return `当前系统状态：总日志 ${s.totalLogs || 0}，欺诈 ${s.fraudCount || 0}，高风险 ${s.highRisk || 0}，已拦截 ${s.blocked || 0}，待处理告警 ${s.pendingAlerts || 0}；实时日志 ${store.logCount} 条，告警 ${store.alertCount} 条。`
}

const QUICK = {
  summary: '请根据当前系统状态，用中文生成一段 3 句话的实时预警摘要，突出风险趋势。',
  rules: '请用中文简要解释反诈规则引擎中 block/warn/allow 三态决策的含义，以及风险分 = 规则分×0.4 + ML分×0.6 的融合逻辑。',
  insight: '请基于当前系统状态，给出 3 条可落地的运营/风控建议。',
}

function quick(key) {
  send(QUICK[key] + '\n\n' + context())
}

async function send(text) {
  const content = (text ?? draft.value).trim()
  if (!content || loading.value) return
  if (!settings.apiKey) {
    ElMessage.warning('请先在设置里填写 API Key')
    showSettings.value = true
    return
  }
  draft.value = ''
  messages.value.push({ role: 'user', content })
  loading.value = true
  scrollBottom()
  try {
    const payload = messages.value.map((m) => ({ role: m.role, content: m.content }))
    const reply = await callProvider(payload)
    messages.value.push({ role: 'assistant', content: reply })
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '调用失败：' + (e?.message || e) })
  } finally {
    loading.value = false
    scrollBottom()
  }
}

async function callProvider(payload) {
  const p = PROVIDERS[settings.provider] || PROVIDERS.anthropic
  const baseUrl = settings.baseUrl || p.url

  if (settings.provider === 'anthropic') {
    const res = await fetch(baseUrl, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'x-api-key': settings.apiKey,
        'anthropic-version': '2023-06-01',
        // Anthropic 允许浏览器直连时需显式声明
        'anthropic-dangerous-direct-browser-access': 'true',
      },
      body: JSON.stringify({ model: settings.model, max_tokens: 1024, messages: payload }),
    })
    if (!res.ok) throw new Error(await readErr(res))
    const data = await res.json()
    return data.content?.[0]?.text || '(空响应)'
  }

  // OpenAI 兼容
  const res = await fetch(baseUrl, {
    method: 'POST',
    headers: { 'content-type': 'application/json', Authorization: 'Bearer ' + settings.apiKey },
    body: JSON.stringify({ model: settings.model, messages: payload }),
  })
  if (!res.ok) throw new Error(await readErr(res))
  const data = await res.json()
  return data.choices?.[0]?.message?.content || '(空响应)'
}

async function readErr(res) {
  try { const t = await res.text(); return t.slice(0, 200) } catch (e) { return 'HTTP ' + res.status }
}
</script>

<style scoped>
.ai-fab {
  position: fixed;
  bottom: 24px;
  right: 24px;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 1000;
  box-shadow: var(--shadow-lg);
  transition: transform .2s, background .2s;
  user-select: none;
}
.ai-fab:hover { transform: scale(1.08); }
.ai-fab.active { background: var(--text-2); }

.ai-panel {
  position: fixed;
  bottom: 88px;
  right: 24px;
  width: 380px;
  height: 520px;
  background: var(--bg-card);
  border: 1px solid var(--border-1);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 1000;
}

.ai-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--border-2);
}
.ai-title { font-size: 14px; font-weight: 700; color: var(--text-1); }
.ai-actions { display: flex; gap: var(--space-3); }
.ai-icon { font-size: 16px; color: var(--text-3); cursor: pointer; }
.ai-icon:hover { color: var(--color-primary); }

.ai-quick { display: flex; gap: var(--space-2); padding: var(--space-3) var(--space-4); border-bottom: 1px solid var(--border-2); }

.ai-messages { flex: 1; overflow-y: auto; padding: var(--space-4); display: flex; flex-direction: column; gap: var(--space-3); }
.ai-empty { text-align: center; color: var(--text-4); font-size: 13px; margin-top: var(--space-8); }
.ai-msg { display: flex; }
.ai-msg.user { justify-content: flex-end; }
.ai-msg.assistant { justify-content: flex-start; }
.ai-bubble {
  max-width: 80%;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.ai-msg.user .ai-bubble { background: var(--color-primary); color: #fff; border-bottom-right-radius: 4px; }
.ai-msg.assistant .ai-bubble { background: var(--bg-hover); color: var(--text-1); border-bottom-left-radius: 4px; }
.ai-bubble.typing { color: var(--text-3); }

.ai-input { display: flex; gap: var(--space-2); padding: var(--space-3); border-top: 1px solid var(--border-2); }

.ai-note { font-size: 12px; color: var(--color-warning); line-height: 1.5; }

.slide-enter-active, .slide-leave-active { transition: transform .2s ease, opacity .2s ease; }
.slide-enter-from, .slide-leave-to { transform: translateY(8px); opacity: 0; }
</style>
