<template>
  <div class="page-card">
    <h3 class="page-title">影响评估（来源/因子变化 → 受影响指标与已发布报告）</h3>
    <div class="toolbar">
      <el-button @click="load">刷新</el-button>
      <span class="hint">来源补录/修订/作废、因子版本替换时自动生成；涉及已发布报告时同时生成重述建议</span>
    </div>
    <el-table :data="impacts" size="small" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="触发类型" width="110">
        <template #default="{ row }"><StatusTag kind="trigger" :value="row.triggerType" /></template>
      </el-table-column>
      <el-table-column prop="period" label="期间" width="80" />
      <el-table-column prop="triggerRef" label="触发说明" min-width="240" show-overflow-tooltip />
      <el-table-column prop="summary" label="影响摘要" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><StatusTag kind="impact" :value="row.status" /></template>
      </el-table-column>
      <el-table-column label="时间" width="150">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="$router.push(`/impacts/${row.id}`)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'
import { fmtTime } from '../store'
import StatusTag from '../components/StatusTag.vue'

const impacts = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const resp = await api.get('/impacts')
    impacts.value = resp.data
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.hint { font-size: 12px; color: #909399; }
</style>
