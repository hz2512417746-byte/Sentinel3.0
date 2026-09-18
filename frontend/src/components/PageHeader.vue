<template>
  <div class="page-header">
    <div class="page-header-top">
      <router-link v-if="backTo" :to="backTo" class="back-btn">
        <ArrowLeft :size="13" />返回
      </router-link>
      <h2 class="page-title">{{ title }}</h2>
      <div class="page-header-extra">
        <slot name="extra" />
      </div>
    </div>
    <div v-if="$slots.default" class="page-header-body">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { ArrowLeft } from '@lucide/vue'
// 页头：标题 + 可选返回链接（带箭头图标）+ 内联控件（extra 插槽）+ 下方说明（default 插槽）
defineProps({
  title: { type: String, required: true },
  backTo: { type: String, default: '' },
})
</script>

<style scoped>
.page-header {
  margin-bottom: var(--space-4);
}
.page-header-top {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 32px;
}
.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--border-1);
  border-radius: var(--radius-sm);
  color: var(--text-2);
  text-decoration: none;
  font-size: 12px;
  flex-shrink: 0;
  transition: color var(--dur-fast), border-color var(--dur-fast);
}
.back-btn:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1);
  margin: 0;
}
.page-header-extra {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-left: var(--space-2);
}
.page-header-body {
  margin-top: var(--space-2);
  font-size: 12px;
  color: var(--text-3);
}
</style>
