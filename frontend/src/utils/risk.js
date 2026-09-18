// 风险相关共享逻辑：配色 + 决策映射。
// 注意：这里的 hex 需与 src/styles/tokens.css 中的语义色保持一致。

// 风险分配色：≥0.7 危险 / ≥0.5 警告 / 其余安全
export const riskColor = (score = 0) =>
  score >= 0.7 ? '#dc2626' : score >= 0.5 ? '#d97706' : '#16a34a'

// 三态决策 → 标签文案与 Element Plus 类型
const decisionMap = {
  block: { text: '阻断', type: 'danger' },
  warn: { text: '预警', type: 'warning' },
  allow: { text: '放行', type: 'success' },
}

export const decisionInfo = (decision) =>
  decisionMap[decision] || { text: decision || '未知', type: 'info' }
