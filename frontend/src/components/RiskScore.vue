<template>
  <span class="risk-score" :style="{ color }">{{ (Number(score) || 0).toFixed(digits) }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { riskColor } from '../utils/risk'

// 带风险配色的分数展示
const props = defineProps({
  score: { type: [Number, String], default: 0 },
  digits: { type: Number, default: 2 },
})

const color = computed(() => riskColor(Number(props.score) || 0))
</script>

<style scoped>
.risk-score {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
</style>
