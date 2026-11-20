<template>
  <div>
    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title" style="margin:0">测量换算与派生指标工作台</h3>
        <el-select v-model="runId" placeholder="选择核算运行" style="width:320px" @change="loadRun">
          <el-option v-for="r in runs" :key="r.id"
                     :label="`${r.runNo}（${r.period} · ${r.purpose === 'OFFICIAL' ? '正式' : '候选'} · ${r.status}）`"
                     :value="r.id" />
        </el-select>
      </div>
      <el-alert type="info" :closable="false"
                title="每次单位转换都是带量纲、条件与区间误差的有向关系；湿基/干基、标况/工况、净热值/低位热值不凭单位名称互换。同一读数被两条链解释为不同能量时保留冲突，不取平均。" />
    </div>

    <div class="page-card" v-if="summary">
      <el-row :gutter="12">
        <el-col :span="5"><el-statistic title="可直接转换" :value="summary.CONVERTIBLE" /></el-col>
        <el-col :span="5"><el-statistic title="需人工桥接" :value="summary.MANUAL_BRIDGE" /></el-col>
        <el-col :span="5"><el-statistic title="量纲不相容" :value="summary.DIMENSION_INCOMPATIBLE" /></el-col>
        <el-col :span="5"><el-statistic title="证据不足" :value="summary.INSUFFICIENT_EVIDENCE" /></el-col>
        <el-col :span="4">
          <el-statistic title="冲突保留">
            <template #default><span class="diff-pos">{{ summary.conflictCount }}</span></template>
          </el-statistic>
        </el-col>
      </el-row>
    </div>

    <el-tabs v-model="tab" class="page-card">
      <!-- 活动量换算证明 -->
      <el-tab-pane label="换算证明" name="conv">
        <el-table :data="conversions" size="small" border>
          <el-table-column label="工厂" width="90">
            <template #default="{ row }">{{ store.plantName(row.plantId) }}</template>
          </el-table-column>
          <el-table-column label="能源" width="90">
            <template #default="{ row }">{{ store.energyLabel(row.energyType) }}</template>
          </el-table-column>
          <el-table-column label="原始读数" width="140">
            <template #default="{ row }">{{ fmtNum(row.sourceQty) }} {{ row.sourceUnit }}</template>
          </el-table-column>
          <el-table-column label="规范量(因子口径)" width="150">
            <template #default="{ row }">
              {{ row.canonicalQty != null ? fmtNum(row.canonicalQty) + ' ' + row.canonicalUnit : '—' }}
            </template>
          </el-table-column>
          <el-table-column label="物理目标量" width="170">
            <template #default="{ row }">
              <span v-if="row.targetQty != null">
                {{ fmtNum(row.targetQty) }} {{ row.targetUnit }}
                <div class="band">[{{ fmtNum(row.lowerQty) }}, {{ fmtNum(row.upperQty) }}]</div>
              </span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="不确定度" width="90">
            <template #default="{ row }">{{ row.uncertaintyPct != null ? row.uncertaintyPct + '%' : '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <StatusTag kind="conversion" :value="row.convStatus" />
              <el-tag v-if="row.conflictFlag" size="small" type="danger" effect="dark" style="margin-left:4px">冲突</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="truncateReason" label="截断/说明" min-width="220" show-overflow-tooltip />
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openPath(row)">路径</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 换算冲突 -->
      <el-tab-pane label="换算冲突" name="conflict">
        <el-table :data="conflicts" size="small" border>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column label="能源" width="100">
            <template #default="{ row }">{{ store.energyLabel(row.energyType) }}</template>
          </el-table-column>
          <el-table-column label="链A 结果" width="150">
            <template #default="{ row }">{{ fmtNum(row.qtyA) }} {{ row.targetUnit }}</template>
          </el-table-column>
          <el-table-column label="链B 结果" width="150">
            <template #default="{ row }">{{ fmtNum(row.qtyB) }} {{ row.targetUnit }}</template>
          </el-table-column>
          <el-table-column prop="basisDiff" label="冲突依据（保留，不取平均）" min-width="320" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'OPEN' ? 'danger' : 'success'">
                {{ row.status === 'OPEN' ? '未解决' : '已解决' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 派生指标 -->
      <el-tab-pane label="派生指标" name="metric">
        <el-table :data="metrics" size="small" border>
          <el-table-column label="工厂" width="90">
            <template #default="{ row }">{{ store.plantName(row.plantId) }}</template>
          </el-table-column>
          <el-table-column label="能源" width="90">
            <template #default="{ row }">{{ store.energyLabel(row.energyType) }}</template>
          </el-table-column>
          <el-table-column label="指标" width="80">
            <template #default="{ row }">{{ metricKindLabel(row.metricKind) }}</template>
          </el-table-column>
          <el-table-column label="数值" width="150">
            <template #default="{ row }">
              {{ row.metricValue != null ? Number(row.metricValue).toFixed(row.displayDecimals) + ' ' + row.metricUnit : '—' }}
            </template>
          </el-table-column>
          <el-table-column label="不确定范围" min-width="200">
            <template #default="{ row }">
              <span v-if="row.lowerValue != null">
                [{{ Number(row.lowerValue).toFixed(row.displayDecimals) }},
                {{ Number(row.upperValue).toFixed(row.displayDecimals) }}]
                <span class="band">{{ row.uncertaintyPct }}%</span>
              </span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }"><StatusTag kind="metric" :value="row.metricStatus" /></template>
          </el-table-column>
          <el-table-column prop="note" label="说明" min-width="220" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <!-- 换算依据与有向边 -->
      <el-tab-pane label="换算依据/边" name="base">
        <div class="toolbar">
          <el-button size="small" type="primary" @click="evidenceDlg.visible = true">登记证据</el-button>
          <el-button size="small" type="primary" @click="baseDlg.visible = true">登记换算依据</el-button>
        </div>
        <el-table :data="bases" size="small" border>
          <el-table-column label="编码" width="170">
            <template #default="{ row }">{{ row.base.code }}</template>
          </el-table-column>
          <el-table-column label="名称" min-width="200">
            <template #default="{ row }">{{ row.base.name }}</template>
          </el-table-column>
          <el-table-column label="换算" width="170">
            <template #default="{ row }">
              {{ row.base.sourceUnit }}({{ row.base.sourceBasis || '—' }}) →
              {{ row.base.targetUnit }}({{ row.base.targetBasis || '—' }})
            </template>
          </el-table-column>
          <el-table-column label="系数" width="110">
            <template #default="{ row }">{{ row.base.factorValue }}</template>
          </el-table-column>
          <el-table-column label="不确定度" width="90">
            <template #default="{ row }">{{ row.base.uncertaintyPct }}%</template>
          </el-table-column>
          <el-table-column label="适用期间" width="150">
            <template #default="{ row }">
              {{ row.base.periodFrom }} ~ {{ row.base.periodTo || '长期' }}
            </template>
          </el-table-column>
          <el-table-column label="证据" width="80">
            <template #default="{ row }">{{ row.base.evidenceId ? '#' + row.base.evidenceId : '无' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><StatusTag kind="base" :value="row.base.status" /></template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.base.status === 'DRAFT'" size="small" link type="warning"
                         @click="approveBase(row.base.id)">批准</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 校准/化验证据 -->
      <el-tab-pane label="证据台账" name="evidence">
        <el-table :data="evidenceList" size="small" border>
          <el-table-column prop="evidenceNo" label="证据编号" width="170" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }"><StatusTag kind="evidence" :value="row.evidenceType" /></template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
          <el-table-column label="标称值" width="130">
            <template #default="{ row }">{{ row.nominalValue }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column label="有效数字" width="80">
            <template #default="{ row }">{{ row.sigFigs ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="不确定度" width="90">
            <template #default="{ row }">{{ row.uncertaintyPct != null ? row.uncertaintyPct + '%' : '—' }}</template>
          </el-table-column>
          <el-table-column label="适用期" width="190">
            <template #default="{ row }">{{ row.effectiveFrom }} ~ {{ row.effectiveTo || '长期' }}</template>
          </el-table-column>
          <el-table-column prop="issuer" label="出具方" width="130" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <ConversionPathDrawer v-model:visible="pathDlg.visible" :conversion-id="pathDlg.id" />

    <!-- 登记证据 -->
    <el-dialog v-model="evidenceDlg.visible" title="登记校准/化验证据" width="560px" destroy-on-close>
      <el-form :model="evidenceDlg.form" label-width="110px" size="small">
        <el-form-item label="证据编号" required><el-input v-model="evidenceDlg.form.evidenceNo" /></el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="evidenceDlg.form.evidenceType" style="width:100%">
            <el-option label="校准证书" value="CALIBRATION" />
            <el-option label="化验报告" value="LAB_ASSAY" />
            <el-option label="密度修订单" value="DENSITY_REVISION" />
            <el-option label="人工桥接说明" value="MANUAL_BRIDGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required><el-input v-model="evidenceDlg.form.title" /></el-form-item>
        <el-form-item label="原始凭证号"><el-input v-model="evidenceDlg.form.sourceRef" /></el-form-item>
        <el-form-item label="出具机构"><el-input v-model="evidenceDlg.form.issuer" /></el-form-item>
        <el-form-item label="出具日期" required><el-input v-model="evidenceDlg.form.issuedDate" placeholder="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="适用期起" required><el-input v-model="evidenceDlg.form.effectiveFrom" placeholder="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="适用期止"><el-input v-model="evidenceDlg.form.effectiveTo" placeholder="空=长期" /></el-form-item>
        <el-form-item label="工厂">
          <el-select v-model="evidenceDlg.form.plantId" clearable style="width:100%">
            <el-option v-for="p in store.plants" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="能源类型"><el-input v-model="evidenceDlg.form.energyType" /></el-form-item>
        <el-form-item label="测量属性" required><el-input v-model="evidenceDlg.form.measuredProp" placeholder="MOISTURE/DENSITY_STD/LHV_DRY..." /></el-form-item>
        <el-form-item label="标称值" required><el-input-number v-model="evidenceDlg.form.nominalValue" :controls="false" style="width:100%" /></el-form-item>
        <el-form-item label="单位" required><el-input v-model="evidenceDlg.form.unit" /></el-form-item>
        <el-form-item label="有效数字"><el-input-number v-model="evidenceDlg.form.sigFigs" :controls="false" style="width:100%" /></el-form-item>
        <el-form-item label="不确定度(%)"><el-input-number v-model="evidenceDlg.form.uncertaintyPct" :controls="false" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="evidenceDlg.visible = false">取消</el-button>
        <el-button type="primary" @click="submitEvidence">提交</el-button>
      </template>
    </el-dialog>

    <!-- 登记换算依据 -->
    <el-dialog v-model="baseDlg.visible" title="登记换算依据（草稿，审核员批准后生成有向换算边）" width="600px" destroy-on-close>
      <el-form :model="baseDlg.form" label-width="110px" size="small">
        <el-form-item label="编码" required><el-input v-model="baseDlg.form.code" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="baseDlg.form.name" /></el-form-item>
        <el-form-item label="能源类型" required><el-input v-model="baseDlg.form.energyType" /></el-form-item>
        <el-form-item label="工厂">
          <el-select v-model="baseDlg.form.plantId" clearable style="width:100%">
            <el-option v-for="p in store.plants" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用期间起" required><el-input v-model="baseDlg.form.periodFrom" placeholder="YYYY-MM" /></el-form-item>
        <el-form-item label="适用期间止"><el-input v-model="baseDlg.form.periodTo" placeholder="空=长期" /></el-form-item>
        <el-form-item label="源单位" required><el-input v-model="baseDlg.form.sourceUnit" /></el-form-item>
        <el-form-item label="源基准"><el-input v-model="baseDlg.form.sourceBasis" placeholder="WET/DRY/STD/OP/LHV" /></el-form-item>
        <el-form-item label="目标单位" required><el-input v-model="baseDlg.form.targetUnit" /></el-form-item>
        <el-form-item label="目标基准"><el-input v-model="baseDlg.form.targetBasis" /></el-form-item>
        <el-form-item label="边类型" required>
          <el-select v-model="baseDlg.form.edgeKind" style="width:100%">
            <el-option label="同量纲单位 UNIT_PREFIX" value="UNIT_PREFIX" />
            <el-option label="基准切换 BASIS_SWITCH" value="BASIS_SWITCH" />
            <el-option label="物性换算 PROPERTY" value="PROPERTY" />
            <el-option label="复合单位 COMPOUND" value="COMPOUND" />
          </el-select>
        </el-form-item>
        <el-form-item label="换算系数" required><el-input-number v-model="baseDlg.form.factorValue" :controls="false" style="width:100%" /></el-form-item>
        <el-form-item label="不确定度(%)" required><el-input-number v-model="baseDlg.form.uncertaintyPct" :controls="false" style="width:100%" /></el-form-item>
        <el-form-item label="证据ID"><el-input-number v-model="baseDlg.form.evidenceId" :controls="false" style="width:100%" /></el-form-item>
        <el-form-item label="条件键"><el-input v-model="baseDlg.form.conditionKey" placeholder="unit/period/plant/moisture_present" /></el-form-item>
        <el-form-item label="条件值"><el-input v-model="baseDlg.form.conditionValue" /></el-form-item>
        <el-form-item label="并行冲突依据">
          <el-switch v-model="baseDlg.form.parallelConflict" />
          <span class="band">两份相反热值/基准依据同时保留时打开</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="baseDlg.visible = false">取消</el-button>
        <el-button type="primary" @click="submitBase">提交草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { store, fmtNum } from '../store'
import StatusTag from '../components/StatusTag.vue'
import ConversionPathDrawer from '../components/ConversionPathDrawer.vue'

const tab = ref('conv')
const runs = ref([])
const runId = ref(null)
const summary = ref(null)
const conversions = ref([])
const conflicts = ref([])
const metrics = ref([])
const bases = ref([])
const evidenceList = ref([])
const pathDlg = reactive({ visible: false, id: null })

const evidenceDlg = reactive({
  visible: false,
  form: { evidenceType: 'LAB_ASSAY', issuedDate: '2025-07-01', effectiveFrom: '2025-07-01' }
})
const baseDlg = reactive({
  visible: false,
  form: { edgeKind: 'PROPERTY', uncertaintyPct: 1, parallelConflict: false }
})

function metricKindLabel(k) {
  return { ENERGY: '能量', EMISSION: '排放量', INTENSITY: '强度' }[k] || k
}

async function loadRun() {
  if (!runId.value) return
  const [s, c, cf, m] = await Promise.all([
    api.get(`/conversion/runs/${runId.value}/summary`),
    api.get(`/conversion/runs/${runId.value}/conversions`),
    api.get(`/conversion/runs/${runId.value}/conflicts`),
    api.get(`/conversion/runs/${runId.value}/metrics`)
  ])
  summary.value = s.data
  conversions.value = c.data
  conflicts.value = cf.data
  metrics.value = m.data
}

async function loadRefs() {
  const [b, e] = await Promise.all([api.get('/conversion/bases'), api.get('/conversion/evidence')])
  bases.value = b.data
  evidenceList.value = e.data
}

function openPath(row) {
  pathDlg.id = row.id
  pathDlg.visible = true
}

async function approveBase(id) {
  await api.post(`/conversion/bases/${id}/approve`)
  ElMessage.success('依据已批准并生成有向换算边')
  loadRefs()
}

async function submitEvidence() {
  await api.post('/conversion/evidence', evidenceDlg.form)
  ElMessage.success('证据已登记')
  evidenceDlg.visible = false
  loadRefs()
}

async function submitBase() {
  await api.post('/conversion/bases', baseDlg.form)
  ElMessage.success('换算依据草稿已创建，待审核员批准')
  baseDlg.visible = false
  loadRefs()
}

onMounted(async () => {
  await store.loadMeta()
  const resp = await api.get('/runs')
  runs.value = resp.data
  const official = resp.data.find(r => r.purpose === 'OFFICIAL')
  if (official) { runId.value = official.id; await loadRun() }
  loadRefs()
})
</script>

<style scoped>
.band { font-size: 11px; color: #909399; }
:deep(.el-statistic__head) { font-size: 12px; color: #606266; }
</style>
