<template>
  <el-drawer :model-value="visible" title="换算证明：逐边求值路径与不确定范围"
             size="640px" @update:model-value="v => $emit('update:visible', v)">
    <div v-if="data" class="conv">
      <el-descriptions :column="2" border size="small" title="活动读数">
        <el-descriptions-item label="来源行">#{{ data.conversion.sourceRowId }}</el-descriptions-item>
        <el-descriptions-item label="能源">{{ store.energyLabel(data.conversion.energyType) }}</el-descriptions-item>
        <el-descriptions-item label="原始读数">
          {{ fmtNum(data.conversion.sourceQty) }} {{ data.conversion.sourceUnit }}
          （{{ data.conversion.sourceDimension }}）
        </el-descriptions-item>
        <el-descriptions-item label="证明状态">
          <StatusTag kind="conversion" :value="data.conversion.convStatus" />
        </el-descriptions-item>
        <el-descriptions-item label="规范量(因子口径)">
          {{ data.conversion.canonicalQty != null
            ? fmtNum(data.conversion.canonicalQty) + ' ' + data.conversion.canonicalUnit : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="物理目标量">
          {{ data.conversion.targetQty != null
            ? fmtNum(data.conversion.targetQty) + ' ' + data.conversion.targetUnit : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="不确定范围" :span="2">
          <span v-if="data.conversion.lowerQty != null">
            [{{ fmtNum(data.conversion.lowerQty) }}, {{ fmtNum(data.conversion.upperQty) }}]
            ｜ 合成相对不确定度 {{ data.conversion.uncertaintyPct }}%
          </span>
          <span v-else>—</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert v-if="data.conversion.truncateReason" type="warning" :closable="false" show-icon
                style="margin:12px 0" :title="data.conversion.truncateReason" />

      <template v-for="(chain, idx) in chains" :key="idx">
        <h4>换算链 {{ idx + 1 }}（目标 {{ chain.targetQty }} {{ chain.targetUnit }}，不确定度 {{ chain.uncertaintyPct }}%）</h4>
        <el-table :data="chain.steps" size="small" border>
          <el-table-column label="步" type="index" width="46" />
          <el-table-column label="边类型" width="96">
            <template #default="{ row }">
              <el-tag size="small" :type="row.virtual ? 'info' : 'primary'">
                {{ row.edgeKind === 'IDENTITY' ? '恒等' : row.edgeKind }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="换算" min-width="150">
            <template #default="{ row }">
              {{ row.fromUnit }}<span v-if="row.fromBasis">({{ row.fromBasis }})</span>
              → {{ row.toUnit }}<span v-if="row.toBasis">({{ row.toBasis }})</span>
            </template>
          </el-table-column>
          <el-table-column label="系数" width="110">
            <template #default="{ row }">
              {{ row.unitAlignFactor && row.unitAlignFactor !== '1' ? '对齐' + row.unitAlignFactor + ' × ' : '' }}{{ row.factor }}
            </template>
          </el-table-column>
          <el-table-column label="不确定度" width="84">
            <template #default="{ row }">{{ row.uncertaintyPct }}%</template>
          </el-table-column>
          <el-table-column label="证据" width="120">
            <template #default="{ row }">
              <span v-if="row.evidenceNo">{{ row.evidenceNo }}</span>
              <el-tag v-else-if="!row.virtual && row.edgeKind !== 'IDENTITY'" size="small" type="danger">缺证截断</el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="truncateReason" label="截断说明" min-width="140" show-overflow-tooltip />
        </el-table>
      </template>

      <template v-if="data.conflicts.length">
        <h4>冲突保留（同一读数的不同物理解释，不取平均）</h4>
        <el-alert v-for="(c, i) in data.conflicts" :key="i" type="error" :closable="false" show-icon
                  style="margin-bottom:8px"
                  :title="`链A ${c.qtyA} ${c.targetUnit} vs 链B ${c.qtyB} ${c.targetUnit}：${c.basisDiff}`" />
      </template>
    </div>
    <el-skeleton v-else :rows="8" animated />
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import api from '../api'
import { store, fmtNum } from '../store'
import StatusTag from './StatusTag.vue'

const props = defineProps({ visible: Boolean, conversionId: Number })
defineEmits(['update:visible'])

const data = ref(null)
const chains = computed(() => {
  const doc = safeParse(data.value?.conversion?.pathJson)
  if (!doc) return []
  if (Array.isArray(doc.paths)) return doc.paths
  if (Array.isArray(doc.steps)) return [{ targetQty: '—', targetUnit: '—', uncertaintyPct: '—', steps: doc.steps }]
  return []
})

function safeParse(s) {
  if (!s) return null
  try { return JSON.parse(s) } catch { return null }
}

watch(() => [props.visible, props.conversionId], async ([v, id]) => {
  if (v && id) {
    data.value = null
    const resp = await api.get(`/conversion/conversions/${id}`)
    data.value = resp.data
  }
}, { immediate: true })
</script>

<style scoped>
.conv h4 { margin: 14px 0 8px; color: #303133; }
</style>
