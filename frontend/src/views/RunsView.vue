<template>
  <div class="page-card">
    <h3 class="page-title">核算运行（活动量 × 批准因子 → 带血缘的排放结果）</h3>
    <div class="toolbar">
      <el-input v-model="period" placeholder="期间 YYYY-MM，如 2025-06" style="width:200px" />
      <el-button type="primary" :loading="creating" @click="createRun">启动正式运行</el-button>
      <span class="hint">运行启动时快照批准因子清单；同一期间同时只允许一个进行中的正式运行</span>
      <el-button style="margin-left:auto" @click="load">刷新</el-button>
    </div>
    <el-table :data="runs" size="small" border v-loading="loading">
      <el-table-column prop="runNo" label="运行编号" width="140" />
      <el-table-column prop="period" label="期间" width="80" />
      <el-table-column label="类型" width="80">
        <template #default="{ row }"><StatusTag kind="purpose" :value="row.purpose" /></template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><StatusTag kind="run" :value="row.status" /></template>
      </el-table-column>
      <el-table-column prop="triggeredBy" label="触发人" width="110" />
      <el-table-column label="启动时间" width="160">
        <template #default="{ row }">{{ fmtTime(row.startedAt) }}</template>
      </el-table-column>
      <el-table-column label="完成时间" width="160">
        <template #default="{ row }">{{ fmtTime(row.completedAt) }}</template>
      </el-table-column>
      <el-table-column prop="error" label="错误" show-overflow-tooltip />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="$router.push(`/runs/${row.id}`)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { fmtTime } from '../store'
import StatusTag from '../components/StatusTag.vue'

const runs = ref([])
const loading = ref(false)
const creating = ref(false)
const period = ref('')
let timer = null

async function load() {
  loading.value = true
  try {
    const resp = await api.get('/runs')
    runs.value = resp.data
  } finally {
    loading.value = false
  }
}

async function createRun() {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(period.value)) {
    ElMessage.warning('请输入正确期间格式，如 2025-06')
    return
  }
  creating.value = true
  try {
    const resp = await api.post('/runs', { period: period.value })
    ElMessage.success(`运行 ${resp.data.runNo} 已启动，因子清单已绑定`)
    load()
  } catch (e) {
    // 409 并发运行等由拦截器提示
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  load()
  timer = setInterval(load, 3000)
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.hint { font-size: 12px; color: #909399; }
</style>
