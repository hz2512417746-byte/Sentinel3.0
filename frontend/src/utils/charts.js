// 图表共享规范：统一配色 / 坐标轴 / tooltip，让各页 ECharts 读起来是一套系统。
// 色值需与 src/styles/tokens.css 的 --chart-* 保持一致。

// 图表系列配色（依次取用）
export const CHART_COLORS = ['#2563eb', '#f59e0b', '#ef4444', '#10b981', '#8b5cf6', '#06b6d4']

// 类别轴通用配置
export const categoryAxis = {
  type: 'category',
  axisLine: { lineStyle: { color: '#e5e7eb' } },
  axisTick: { show: false },
  axisLabel: { color: '#6b7280', fontSize: 11 },
}

// 数值轴通用配置
export const valueAxis = (name = '') => ({
  type: 'value',
  name,
  nameTextStyle: { color: '#9ca3af', fontSize: 11 },
  axisLabel: { color: '#6b7280', fontSize: 11 },
  splitLine: { lineStyle: { color: '#f3f4f6' } },
})

// 通用 tooltip
export const baseTooltip = {
  backgroundColor: '#ffffff',
  borderColor: '#e5e7eb',
  textStyle: { color: '#1f2937', fontSize: 12 },
}

// 统一平滑动画：数据刷新时渐变过渡，而非整图重绘闪烁
export const smoothAnimation = {
  animationDuration: 600,
  animationEasing: 'cubicOut',
  animationDurationUpdate: 500,
  animationEasingUpdate: 'cubicOut',
}
