<template>
  <div class="page-card">
    <h3 class="page-title">披露报告（冻结 → 复核 → 审批 → 发布 / 重述）</h3>
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建披露报告</el-button>
      <span class="hint">勾稽不平或存在待核指标时报告保持“待核”，不能审批发布，差额不能归零</span>
    </div>
    <el-table :data="reports" size="small" border v-loading="loading">
      <el-table-column prop="code" label="报告编号" width="140" />
      <el-table-column prop="title" label="标题" min-width="220" />
      <el-table-column prop="period" label="期间" width="80" />
      <el-table-column label="状态" width="140">
        <template #default="{ row }"><StatusTag kind="report" :value="row.status" /></template>
      </el-table-column>
      <el-table-column prop="createdBy" label="创建人" width="90" />
      <el-table-column label="创建时间" width="150">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="$router.push(`/reports/${row.id}`)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlg.visible" title="新建披露报告" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="标题"><el-input v-model="dlg.title" placeholder="如 2025年6月集团碳排放披露报告" /></el-form-item>
        <el-form-item label="期间">
          <el-input v-model="dlg.period" placeholder="YYYY-MM" @input="loadRuns" />
        </el-form-item>
        <el-form-item label="核算运行">
          <el-select v-model="dlg.runId" style="width:100%" placeholder="选择已完成的运行">
            <el-option v-for="r in dlg.runs" :key="r.id"
                       :label="`${r.runNo}（${r.period} · ${r.purpose === 'OFFICIAL' ? '正式' : '候选'}）`"
                       :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" @click="create">创建并冻结首版</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { fmtTime } from '../store'
import StatusTag from '../components/StatusTag.vue'

const reports = ref([])
const loading = ref(false)
const dlg = reactive({ visible: false, title: '', period: '', runId: null, runs: [] })

async function load() {
  loading.value = true
  try {
    const resp = await api.get('/reports')
    reports.value = resp.data
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  Object.assign(dlg, { visible: true, title: '', period: '', runId: null, runs: [] })
}

async function loadRuns() {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(dlg.period)) return
  const resp = await api.get('/runs')
  dlg.runs = resp.data.filter(r => r.period === dlg.period && r.status === 'COMPLETED')
}

async function create() {
  if (!dlg.title || !dlg.period || !dlg.runId) {
    ElMessage.warning('请填写标题、期间并选择运行')
    return
  }
  await api.post('/reports', { title: dlg.title, period: dlg.period, runId: dlg.runId })
  ElMessage.success('报告已创建并冻结首版')
  dlg.visible = false
  load()
}

onMounted(load)
</script>

<style scoped>
.hint { font-size: 12px; color: #909399; }
</style>
