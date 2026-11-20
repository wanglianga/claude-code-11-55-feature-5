<template>
  <el-tag :type="tagType" size="small" disable-transitions>{{ label }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { STATUS_MAPS } from '../store'

const props = defineProps({
  kind: { type: String, required: true },
  value: { type: String, default: '' }
})

const entry = computed(() => (STATUS_MAPS[props.kind] || {})[props.value])
const label = computed(() => entry.value?.[0] || props.value || '-')
const tagType = computed(() => entry.value?.[1] || 'info')
</script>
