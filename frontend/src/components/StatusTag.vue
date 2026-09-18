<template>
  <el-tag :type="info.type" :size="size">{{ info.text }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { decisionInfo } from '../utils/risk'

// 三态决策标签：block/warn/allow → 阻断/预警/放行
const props = defineProps({
  decision: { type: String, default: '' },
  size: { type: String, default: 'small' },
})

const info = computed(() => decisionInfo(props.decision))
</script>
