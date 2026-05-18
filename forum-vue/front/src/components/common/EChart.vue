<template>
  <v-chart class="chart" :option="option" :update-options="updateOptions" autoresize />
</template>

<script setup>
import { computed } from 'vue'
import { useEChart } from '@scripts/components/common/EChart'

const props = defineProps({
  option: Object,
  notMerge: { type: Boolean, default: false },
})

const updateOptions = computed(() => ({ notMerge: props.notMerge }))

const { VChart } = useEChart()
</script>

