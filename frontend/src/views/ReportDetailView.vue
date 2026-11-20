<template>
  <div v-if="data">
    <div class="page-card">
      <div class="head-row">
        <h3 class="page-title">{{ data.report.title }}</h3>
        <div class="actions">
          <el-button size="small" @click="freezeDlg.visible = true">冻结新版本</el-button>
          <el-button size="small" type="warning" :disabled="data.report.status !== 'PENDING_REVIEW'"
                     @click="approve">审批</el-button>
          <el-button size="small" type="success" :disabled="data.report.status !== 'APPROVED'"
                     @click="publish">发布</el-button>
        </div>
      </div>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="编号">{{ data.report.code }}</el-descriptions-item>
        <el-descriptions-item label="期间">{{ data.report.period }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag kind="report" :value="data.report.status" />
        </el-descriptions-item>
        <el-descriptions-item label="创建">{{ data.report.createdBy }} · {{ fmtTime(data.report.createdAt) }}</el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="data.report.status === 'RECONCILE_FAILED'" type="error" :closable="false"
                style="margin-top:10px" show-icon
                title="报告保持待核：产品线合计与工厂明细不勾稽，或存在缺少适用因子的待核指标；不能为了按期提交把差额归零" />
    </div>

    <div class="page-card">
      <h3 class="page-title">版本历史（披露历史不可改写）</h3>
      <el-table :data="data.versions" size="small" border
                :row-class-name="({ row }) => row.id === selectedVersionId ? 'sel-row' : ''"
                @row-click="row => selectVersion(row.id)">
        <el-table-column label="版本" width="60">
          <template #default="{ row }">v{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><StatusTag kind="version" :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="总量 (tCO2e)" width="120">
          <template #default="{ row }"><b>{{ fmtNum(row.totalTco2e, 2) }}</b></template>
        </el-table-column>
        <el-table-column label="范围一/二/三" width="220">
          <template #default="{ row }">
            {{ fmtNum(row.totalScope1, 2) }} / {{ fmtNum(row.totalScope2, 2) }} / {{ fmtNum(row.totalScope3, 2) }}
          </template>
        </el-table-column>
        <el-table-column label="勾稽" width="110">
          <template #default="{ row }"><StatusTag kind="reconcile" :value="row.reconcileStatus" /></template>
        </el-table-column>
        <el-table-column label="待核项" width="70">
          <template #default="{ row }">
            <span :class="{ 'diff-pos': row.pendingCount > 0 }">{{ row.pendingCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="运行" width="80">
          <template #default="{ row }">
            <el-link type="primary" @click.stop="$router.push(`/runs/${row.runId}`)">#{{ row.runId }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="冻结" width="140">
          <template #default="{ row }">{{ row.frozenBy }} · {{ fmtTime(row.frozenAt) }}</template>
        </el-table-column>
        <el-table-column label="发布时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.publishedAt) }}</template>
        </el-table-column>
        <el-table-column label="重述自" width="80">
          <template #default="{ row }">{{ row.restatementOfVersionId ? 'v' + versionNoOf(row.restatementOfVersionId) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="140" show-overflow-tooltip />
      </el-table>
    </div>

    <div class="page-card" v-if="versionDetail">
      <h3 class="page-title">v{{ versionDetail.version.versionNo }} 披露条目</h3>
      <el-table :data="versionDetail.items" size="small" border>
        <el-table-column label="工厂" width="100">
          <template #default="{ row }">{{ store.plantName(row.plantId) }}</template>
        </el-table-column>
        <el-table-column label="产品线" width="100">
          <template #default="{ row }">{{ store.lineName(row.productLineId) }}</template>
        </el-table-column>
        <el-table-column prop="scope" label="范围" width="80" />
        <el-table-column label="能源类型" width="100">
          <template #default="{ row }">{{ store.energyLabel(row.energyType) }}</template>
        </el-table-column>
        <el-table-column label="活动量" width="130">
          <template #default="{ row }">{{ fmtNum(row.activityQty) }}</template>
        </el-table-column>
        <el-table-column label="排放量 (tCO2e)" width="130">
          <template #default="{ row }"><b>{{ fmtNum(row.emissionTco2e) }}</b></template>
        </el-table-column>
        <el-table-column label="状态">
          <template #default="{ row }"><StatusTag kind="result" :value="row.itemStatus" /></template>
        </el-table-column>
      </el-table>

      <h4>勾稽核对（产品线合计 vs 工厂明细）</h4>
      <el-table :data="versionDetail.reconciliations" size="small" border>
        <el-table-column prop="label" label="核对项" min-width="220" />
        <el-table-column label="工厂明细合计" width="140">
          <template #default="{ row }">{{ fmtNum(row.plantDetailSum, 2) }}</template>
        </el-table-column>
        <el-table-column label="产品线合计" width="140">
          <template #default="{ row }">{{ fmtNum(row.productLineTotal, 2) }}</template>
        </el-table-column>
        <el-table-column label="差异" width="120">
          <template #default="{ row }">
            <span :class="{ 'diff-pos': Number(row.diff) !== 0 }">{{ fmtNum(row.diff, 2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结论" width="120">
          <template #default="{ row }"><StatusTag kind="reconcile" :value="row.status" /></template>
        </el-table-column>
      </el-table>

      <h4>披露单元格追溯（能量/排放/强度：可复算换算路径与不确定范围）</h4>
      <el-table :data="versionDetail.cellTraces || []" size="small" border>
        <el-table-column label="工厂" width="90">
          <template #default="{ row }">{{ store.plantName(row.plantId) }}</template>
        </el-table-column>
        <el-table-column label="能源" width="90">
          <template #default="{ row }">{{ store.energyLabel(row.energyType) }}</template>
        </el-table-column>
        <el-table-column label="单元格" width="80">
          <template #default="{ row }">{{ cellTypeLabel(row.cellType) }}</template>
        </el-table-column>
        <el-table-column label="展示值（各自单位/小数位）" width="180">
          <template #default="{ row }">
            {{ row.displayedValue != null
              ? Number(row.displayedValue).toFixed(row.displayDecimals) + ' ' + row.displayedUnit : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="不确定范围" min-width="180">
          <template #default="{ row }">
            <span v-if="row.lowerValue != null">
              [{{ Number(row.lowerValue).toFixed(row.displayDecimals) }},
              {{ Number(row.upperValue).toFixed(row.displayDecimals) }}]
            </span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="追溯状态" width="110">
          <template #default="{ row }"><StatusTag kind="trace" :value="row.traceStatus" /></template>
        </el-table-column>
        <el-table-column prop="evidenceSummary" label="引用证据" min-width="180" show-overflow-tooltip />
        <el-table-column prop="frozenNote" label="冻结后补证差异" min-width="220" show-overflow-tooltip />
      </el-table>
    </div>

    <div class="page-card" v-if="data.restatements.length">
      <h3 class="page-title">重述记录</h3>
      <el-table :data="data.restatements" size="small" border>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><StatusTag kind="restatement" :value="row.status" /></template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip />
        <el-table-column prop="diffSummary" label="差异说明" min-width="260" show-overflow-tooltip />
        <el-table-column label="新版本" width="80">
          <template #default="{ row }">{{ row.newVersionId ? 'v' + versionNoOf(row.newVersionId) : '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="freezeDlg.visible" title="冻结新版本" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="核算运行">
          <el-select v-model="freezeDlg.runId" style="width:100%">
            <el-option v-for="r in freezeDlg.runs" :key="r.id"
                       :label="`${r.runNo}（${r.period} · ${r.purpose === 'OFFICIAL' ? '正式' : '候选'}）`"
                       :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="freezeDlg.note" placeholder="可选" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="freezeDlg.visible = false">取消</el-button>
        <el-button type="primary" @click="freeze">冻结</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { store, fmtNum, fmtTime } from '../store'
import StatusTag from '../components/StatusTag.vue'

const route = useRoute()
const data = ref(null)
const versionDetail = ref(null)
const selectedVersionId = ref(null)
const freezeDlg = reactive({ visible: false, runId: null, note: '', runs: [] })

async function load() {
  const resp = await api.get(`/reports/${route.params.id}`)
  data.value = resp.data
  const latest = resp.data.versions[0]
  if (latest && !selectedVersionId.value) selectedVersionId.value = latest.id
  if (selectedVersionId.value) await selectVersion(selectedVersionId.value)
}

async function selectVersion(versionId) {
  selectedVersionId.value = versionId
  const resp = await api.get(`/reports/versions/${versionId}`)
  versionDetail.value = resp.data
}

function versionNoOf(versionId) {
  const v = (data.value?.versions || []).find(x => x.id === versionId)
  return v ? v.versionNo : '?'
}

function cellTypeLabel(t) {
  return { ENERGY: '能量', EMISSION: '排放', INTENSITY: '强度' }[t] || t
}

async function approve() {
  await ElMessageBox.confirm('确认审批通过该报告当前版本？', '审批', { type: 'warning' })
  await api.post(`/reports/${route.params.id}/approve`)
  ElMessage.success('已审批')
  load()
}

async function publish() {
  await ElMessageBox.confirm(
    '发布后版本内容不可改写；若后续发现来源缺失，将通过重述产生新版本。确认发布？',
    '发布报告', { type: 'warning' })
  await api.post(`/reports/${route.params.id}/publish`)
  ElMessage.success('已发布')
  load()
}

async function freeze() {
  if (!freezeDlg.runId) {
    ElMessage.warning('请选择核算运行')
    return
  }
  await api.post(`/reports/${route.params.id}/freeze`, {
    runId: freezeDlg.runId, note: freezeDlg.note || null
  })
  ElMessage.success('已冻结新版本')
  freezeDlg.visible = false
  selectedVersionId.value = null
  load()
}

onMounted(async () => {
  await store.loadMeta()
  await load()
  const resp = await api.get('/runs')
  freezeDlg.runs = resp.data.filter(
    r => r.period === data.value.report.period && r.status === 'COMPLETED')
})
</script>

<style scoped>
.head-row { display: flex; justify-content: space-between; align-items: center; }
.head-row .actions { display: flex; gap: 8px; }
h4 { margin: 16px 0 8px; }
:deep(.sel-row) { background: #ecf5ff; }
</style>
