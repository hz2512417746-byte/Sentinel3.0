<template>
  <el-card class="chart-card" shadow="never">
    <template #header>
      <div class="chart-card-header">
        <span class="chart-card-title">{{ title }}</span>
        <slot name="extra" />
      </div>
    </template>
    <slot />
  </el-card>
</template>

<script setup>
// 图表统一容器：统一标题栏与卡片外壳，图表内容由默认插槽传入
defineProps({
  title: { type: String, default: '' },
})
</script>

<style scoped>
.chart-card :deep(.el-card__header) {
  padding: var(--space-3) var(--space-4);
}
.chart-card :deep(.el-card__body) {
  padding: var(--space-4);
}
.chart-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.chart-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}
</style>
