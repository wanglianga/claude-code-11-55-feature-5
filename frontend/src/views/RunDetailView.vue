<template>
  <div v-if="data">
    <div class="page-card">
      <h3 class="page-title">运行详情 {{ data.run.runNo }}</h3>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="期间">{{ data.run.period }}</el-descriptions-item>
        <el-descriptions-item label="类型"><StatusTag kind="purpose" :value="data.run.purpose" /></el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag kind="run" :value="data.run.status" /></el-descriptions-item>
        <el-descriptions-item label="触发人">{{ data.run.triggeredBy }}</el-descriptions-item>
        <el-descriptions-item label="启动时间">{{ fmtTime(data.run.startedAt) }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ fmtTime(data.run.completedAt) }}</el-descriptions-item>
        <el-descriptions-item label="结果数">{{ data.results.length }}</el-descriptions-item>
        <el-descriptions-item label="待核指标">
          <span :class="{ 'diff-pos': pendingCount > 0 }">{{ pendingCount }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="page-card">
      <h3 class="page-title">启动时绑定的因子清单（快照）</h3>
      <el-table :data="data.bindings" size="small" border>
        <el-table-column prop="factorCode" label="因子编码" width="150" />
        <el-table-column prop="region" label="地域" width="100" />
        <el-table-column label="能源类型" width="110">
          <template #default="{ row }">{{ store.energyLabel(row.energyType) }}</template>
        </el-table-column>
        <el-table-column label="因子值" width="140">
          <template #default="{ row }">{{ row.value }} kgCO2e/{{ unitOf(row.energyType) }}</template>
        </el-table-column>
        <el-table-column label="适用期">
          <template #default="{ row }">{{ row.validFrom }} ~ {{ row.validTo || '长期' }}</template>
        </el-table-column>
        <el-table-column prop="factorVersionId" label="版本ID" width="80" />
      </el-table>
      <div class="hint">运行期间即使因子被替换，本运行仍使用上述快照；替换后的因子只进入新的运行。</div>
    </div>

    <div class="page-card">
      <h3 class="page-title">测量换算证明（每段活动量 → 可用换算链）</h3>
      <div v-if="convSummary" class="conv-summary">
        <el-tag type="success">可直接转换 {{ convSummary.CONVERTIBLE }}</el-tag>
        <el-tag type="warning">需人工桥接 {{ convSummary.MANUAL_BRIDGE }}</el-tag>
        <el-tag type="danger">量纲不相容 {{ convSummary.DIMENSION_INCOMPATIBLE }}</el-tag>
        <el-tag type="danger">证据不足 {{ convSummary.INSUFFICIENT_EVIDENCE }}</el-tag>
        <el-tag type="danger" effect="dark">冲突保留 {{ convSummary.conflictCount }}</el-tag>
        <el-button size="small" link type="primary" @click="$router.push('/conversion')">
          打开测量换算工作台查看逐边路径与范围 →
        </el-button>
      </div>
    </div>

    <div class="page-card">
      <h3 class="page-title">排放结果（点击“血缘”下钻到来源行与因子版本）</h3>
      <el-table :data="data.results" size="small" border>
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
          <template #default="{ row }">{{ fmtNum(row.activityQty) }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column label="因子值" width="110">
          <template #default="{ row }">{{ row.factorValue ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="排放量 (tCO2e)" width="130">
          <template #default="{ row }"><b>{{ fmtNum(row.emissionTco2e) }}</b></template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><StatusTag kind="result" :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openLineage(row)">血缘</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <LineageDrawer v-model:visible="lineage.visible" :result-id="lineage.resultId" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api'
import { store, fmtNum, fmtTime } from '../store'
import StatusTag from '../components/StatusTag.vue'
import LineageDrawer from '../components/LineageDrawer.vue'

const route = useRoute()
const data = ref(null)
const convSummary = ref(null)
const lineage = reactive({ visible: false, resultId: null })

const pendingCount = computed(() =>
  (data.value?.results || []).filter(r =>
    r.status === 'PENDING_FACTOR' || r.status === 'PENDING_CONVERSION').length)

function unitOf(energyType) {
  return store.energyTypes.find(t => t.code === energyType)?.unit || ''
}

function openLineage(row) {
  lineage.resultId = row.id
  lineage.visible = true
}

onMounted(async () => {
  await store.loadMeta()
  const resp = await api.get(`/runs/${route.params.id}`)
  data.value = resp.data
  try {
    const cs = await api.get(`/conversion/runs/${route.params.id}/summary`)
    convSummary.value = cs.data
  } catch { convSummary.value = null }
})
</script>

<style scoped>
.hint { font-size: 12px; color: #909399; margin-top: 8px; }
.conv-summary { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
</style>
