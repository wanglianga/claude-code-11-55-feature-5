<template>
  <div v-if="data">
    <div class="page-card">
      <h3 class="page-title">影响评估 #{{ data.assessment.id }}</h3>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="触发类型">
          <StatusTag kind="trigger" :value="data.assessment.triggerType" />
        </el-descriptions-item>
        <el-descriptions-item label="期间">{{ data.assessment.period }}</el-descriptions-item>
        <el-descriptions-item label="触发说明" :span="2">{{ data.assessment.triggerRef }}</el-descriptions-item>
        <el-descriptions-item label="影响摘要" :span="2">{{ data.assessment.summary }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag kind="impact" :value="data.assessment.status" />
        </el-descriptions-item>
        <el-descriptions-item label="候选运行">
          <el-link v-if="data.assessment.candidateRunId" type="primary"
                   @click="$router.push(`/runs/${data.assessment.candidateRunId}`)">
            查看候选运行 #{{ data.assessment.candidateRunId }}
          </el-link>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="page-card">
      <h3 class="page-title">受影响指标对比（旧口径 vs 重算）</h3>
      <el-table :data="data.items" size="small" border>
        <el-table-column prop="metric" label="指标（工厂 / 产品线 / 范围）" min-width="220" />
        <el-table-column label="旧值 (tCO2e)" width="130">
          <template #default="{ row }">{{ fmtNum(row.oldValue) }}</template>
        </el-table-column>
        <el-table-column label="新值 (tCO2e)" width="130">
          <template #default="{ row }">{{ fmtNum(row.newValue) }}</template>
        </el-table-column>
        <el-table-column label="差异" width="120">
          <template #default="{ row }">
            <span :class="Number(row.diff) > 0 ? 'diff-pos' : 'diff-neg'">
              {{ Number(row.diff) > 0 ? '+' : '' }}{{ fmtNum(row.diff) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="受影响报告" width="140">
          <template #default="{ row }">
            <el-link v-if="row.affectedReportId" type="primary"
                     @click="$router.push(`/reports/${row.affectedReportId}`)">
              查看报告 #{{ row.affectedReportId }}
            </el-link>
            <span v-else>（无已发布报告）</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!data.items.length" description="重算后与既有口径无显著差异" :image-size="70" />
    </div>

    <div class="page-card" v-if="data.restatements.length">
      <h3 class="page-title">重述建议</h3>
      <el-table :data="data.restatements" size="small" border>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><StatusTag kind="restatement" :value="row.status" /></template>
        </el-table-column>
        <el-table-column prop="diffSummary" label="差异说明" min-width="300" show-overflow-tooltip />
        <el-table-column label="生成时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PROPOSED'">
              <el-button size="small" type="success" @click="accept(row)">接受并冻结新版本</el-button>
              <el-button size="small" type="danger" plain @click="reject(row)">驳回</el-button>
            </template>
            <el-link v-else-if="row.newVersionId" type="primary"
                     @click="$router.push(`/reports/${row.reportId}`)">查看报告</el-link>
          </template>
        </el-table-column>
      </el-table>
      <el-alert type="info" :closable="false" style="margin-top:10px"
                title="接受重述不会覆盖已发布报告：系统基于候选运行冻结新版本，经审批发布后旧版本标记为“已重述”，披露历史完整保留" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { fmtNum, fmtTime } from '../store'
import StatusTag from '../components/StatusTag.vue'

const route = useRoute()
const data = ref(null)

async function load() {
  const resp = await api.get(`/impacts/${route.params.id}`)
  data.value = resp.data
}

async function accept(row) {
  await ElMessageBox.confirm(
    '接受重述建议：将基于候选运行为受影响报告冻结新版本（旧发布版本保持不动，需重新审批发布）。继续？',
    '接受重述', { type: 'warning' })
  const resp = await api.post(`/impacts/restatements/${row.id}/accept`)
  ElMessage.success(`已冻结新版本 v${resp.data.newVersion.versionNo}，报告进入待复核`)
  load()
}

async function reject(row) {
  const { value } = await ElMessageBox.prompt('驳回说明', '驳回重述建议', { inputValue: '' })
  await api.post(`/impacts/restatements/${row.id}/reject`, { reason: value || '' })
  ElMessage.success('已驳回')
  load()
}

onMounted(load)
</script>
