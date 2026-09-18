<template>
  <div class="empty-state">
    <el-icon class="empty-icon"><component :is="icon" /></el-icon>
    <div class="empty-text">{{ text }}</div>
  </div>
</template>

<script setup>
// 空状态占位：统一图标 + 文案（icon 为 Element Plus 图标名）
defineProps({
  text: { type: String, default: '暂无数据' },
  icon: { type: String, default: 'Box' },
})
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-8);
  color: var(--text-4);
}
.empty-icon {
  font-size: 40px;
  margin-bottom: var(--space-2);
}
.empty-text {
  font-size: 13px;
}
</style>
