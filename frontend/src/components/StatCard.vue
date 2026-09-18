<template>
  <el-card class="stat-card" shadow="never">
    <div class="stat-row">
      <div v-if="icon" class="stat-icon" :style="{ color: iconColor, background: iconBg }">
        <component :is="icon" :size="18" />
      </div>
      <div class="stat-main">
        <div class="stat-value" :style="{ color: color || 'var(--text-1)' }">{{ displayValue }}</div>
        <div class="stat-label">{{ label }}</div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { useTransition } from '@vueuse/core'

// KPI 指标卡：图标 + 数值 + 标签；数字变化时平滑滚动（useTransition）
const props = defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: '' },
  color: { type: String, default: '' },
  icon: { type: [Object, Function], default: null },
  iconColor: { type: String, default: '' },
})

// 数值滚动仅对数字生效；带单位/千分位的字符串直接展示
const isNum = computed(() => typeof props.value === 'number')
const target = computed(() => (isNum.value ? props.value : 0))
const eased = useTransition(target, { duration: 700 })
const displayValue = computed(() =>
  isNum.value ? Math.round(eased.value).toLocaleString() : props.value
)

const iconColor = computed(() => props.iconColor || props.color || 'var(--color-primary)')
const iconBg = computed(() => {
  const c = iconColor.value
  return c.startsWith('#') ? c + '1a' : 'var(--el-color-primary-light-9)'
})
</script>

<style scoped>
.stat-card :deep(.el-card__body) {
  padding: var(--space-4);
}
.stat-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.stat-icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
}
.stat-main {
  min-width: 0;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.stat-label {
  font-size: 12px;
  color: var(--text-3);
  margin-top: var(--space-1);
}
</style>
