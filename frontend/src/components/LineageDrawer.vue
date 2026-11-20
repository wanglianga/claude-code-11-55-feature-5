<template>
  <el-drawer :model-value="visible" title="血缘下钻：排放结果 ← 来源数据 + 因子版本"
             size="620px" @update:model-value="v => $emit('update:visible', v)">
    <div v-if="data" class="lineage">
      <el-descriptions :column="2" border size="small" title="排放结果">
        <el-descriptions-item label="工厂">{{ store.plantName(data.result.plantId) }}</el-descriptions-item>
        <el-descriptions-item label="产品线">{{ store.lineName(data.result.productLineId) }}</el-descriptions-item>
        <el-descriptions-item label="范围">{{ data.result.scope }}</el-descriptions-item>
        <el-descriptions-item label="能源类型">{{ store.energyLabel(data.result.energyType) }}</el-descriptions-item>
        <el-descriptions-item label="活动量">{{ fmtNum(data.result.activityQty) }} {{ data.result.unit }}</el-descriptions-item>
        <el-descriptions-item label="排放量">
          <b>{{ fmtNum(data.result.emissionTco2e) }}</b> tCO2e
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag kind="result" :value="data.result.status" />
        </el-descriptions-item>
      </el-descriptions>

      <template v-if="data.factorVersion">
        <h4>排放因子版本</h4>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="因子">{{ data.factorVersion.factorCode }} {{ data.factorVersion.factorName }}</el-descriptions-item>
          <el-descriptions-item label="版本">v{{ data.factorVersion.versionNo }}</el-descriptions-item>
          <el-descriptions-item label="因子值">{{ data.factorVersion.value }} kgCO2e/{{ data.result.unit }}</el-descriptions-item>
          <el-descriptions-item label="地域">{{ data.factorVersion.region }}</el-descriptions-item>
          <el-descriptions-item label="适用期">{{ data.factorVersion.validFrom }} ~ {{ data.factorVersion.validTo || '长期' }}</el-descriptions-item>
          <el-descriptions-item label="状态"><StatusTag kind="factor" :value="data.factorVersion.status" /></el-descriptions-item>
          <el-descriptions-item label="批准人">{{ data.factorVersion.approvedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="批准时间">{{ fmtTime(data.factorVersion.approvedAt) }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <el-alert v-else type="warning" :closable="false" style="margin-top:12px"
                title="缺少适用的批准因子：该指标列为待核，未使用集团平均因子兜底" />

      <h4>来源数据（{{ data.sources.length }} 行）</h4>
      <el-table :data="data.sources" size="small" border>
        <el-table-column prop="batchNo" label="来源批次" width="180" />
        <el-table-column prop="rowNo" label="行号" width="60" />
        <el-table-column label="数量" width="110">
          <template #default="{ row }">{{ fmtNum(row.qtyUsed) }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column prop="voucherNo" label="供应商凭证" width="110">
          <template #default="{ row }">{{ row.voucherNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="批次状态" width="90">
          <template #default="{ row }"><StatusTag kind="batch" :value="row.batchStatus" /></template>
        </el-table-column>
        <el-table-column label="核验人" width="80">
          <template #default="{ row }">{{ row.verifiedBy || '-' }}</template>
        </el-table-column>
      </el-table>
    </div>
    <el-skeleton v-else :rows="8" animated />
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import api from '../api'
import { store, fmtNum, fmtTime } from '../store'
import StatusTag from './StatusTag.vue'

const props = defineProps({
  visible: Boolean,
  resultId: Number
})
defineEmits(['update:visible'])

const data = ref(null)
watch(() => [props.visible, props.resultId], async ([v, id]) => {
  if (v && id) {
    data.value = null
    const resp = await api.get(`/results/${id}/lineage`)
    data.value = resp.data
  }
}, { immediate: true })
</script>

<style scoped>
.lineage h4 { margin: 16px 0 8px; color: #303133; }
</style>
