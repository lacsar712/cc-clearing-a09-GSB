<template>
  <div class="page">
    <h2 class="page-title">回执对账</h2>
    <p class="page-desc">录入会员回执净额，与系统轧差净头寸按会员比对；存在差异的会员会以红色标出（正=应收 / 负=应付，口径一致）</p>

    <div class="card-panel">
      <div class="toolbar">
        <el-select
          v-model="selectedRunId"
          placeholder="选择轧差批次"
          style="width: 360px"
          filterable
          @change="onSelectRun"
        >
          <el-option
            v-for="r in runs"
            :key="r.runId"
            :value="r.runId"
            :label="`${r.settleDate} ${r.currency} · ${r.status} · ${r.runId.slice(0, 8)}`"
          />
        </el-select>
        <el-button @click="loadAll">刷新</el-button>
        <template v-if="auth.isOperator">
          <el-button :disabled="!data" @click="fillFromSystem">以系统净额填充</el-button>
          <el-button
            type="primary"
            :disabled="!data"
            :loading="saving"
            @click="save"
          >保存回执</el-button>
        </template>
        <el-tag v-if="data" :type="data.allMatched ? 'success' : data.missingCount === data.totalMembers ? 'info' : 'danger'">
          {{ overallText }}
        </el-tag>
      </div>
      <el-alert
        v-if="!auth.isOperator"
        type="info"
        :closable="false"
        show-icon
        title="当前为只读账号，仅可查看对账结果；录入回执净额请使用操作员（operator）账号。"
      />
    </div>

    <div v-if="data" class="summary card-panel" style="margin-top:16px">
      <div class="stat">
        <div class="label">比对会员数</div>
        <div class="value">{{ data.totalMembers }}</div>
      </div>
      <div class="stat ok">
        <div class="label">一致</div>
        <div class="value">{{ data.matchedCount }}</div>
      </div>
      <div class="stat bad">
        <div class="label">差异</div>
        <div class="value">{{ data.mismatchCount }}</div>
      </div>
      <div class="stat warn">
        <div class="label">未录入回执</div>
        <div class="value">{{ data.missingCount }}</div>
      </div>
    </div>

    <div class="card-panel" style="margin-top:16px" v-loading="loading">
      <el-table
        v-if="data"
        :data="mergedRows"
        :row-class-name="rowClassName"
        stripe
      >
        <el-table-column label="会员" min-width="240">
          <template #default="{ row }">
            <span class="mono">{{ row.memberId }}</span>
            <div>{{ row.memberName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="90" />
        <el-table-column prop="systemNetAmount" label="系统净头寸" min-width="160" />
        <el-table-column label="回执净额（录入）" min-width="200">
          <template #default="{ row }">
            <el-input-number
              v-model="inputs[row.memberId]"
              :controls="false"
              :disabled="!auth.isOperator"
              :precision="8"
              placeholder="待录入"
              style="width: 170px"
            />
          </template>
        </el-table-column>
        <el-table-column label="差额（回执 − 系统）" min-width="170">
          <template #default="{ row }">
            <span v-if="row.diff == null" class="muted">—</span>
            <span v-else :class="{ 'diff-bad': !row.matchedLocal }">{{ formatNum(row.diff) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.localStatus)" size="small">
              {{ statusText(row.localStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="回执更新时间" min-width="180">
          <template #default="{ row }">
            {{ row.updatedAt ? new Date(row.updatedAt).toLocaleString() : '—' }}
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="请选择一个轧差批次进行回执对账" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const EPS = 1e-8

const auth = useAuthStore()
const route = useRoute()

const loading = ref(false)
const saving = ref(false)
const runs = ref([])
const selectedRunId = ref('')
const data = ref(null)
const inputs = ref({})

const overallText = computed(() => {
  if (!data.value) return ''
  if (data.value.allMatched) return '全部一致'
  if (data.value.missingCount === data.value.totalMembers) return '尚未录入回执'
  return `存在 ${Number(data.value.mismatchCount) + Number(data.value.missingCount)} 处待处理`
})

const mergedRows = computed(() => {
  if (!data.value) return []
  return data.value.rows.map((r) => {
    const input = inputs.value[r.memberId]
    const diff = input == null || Number.isNaN(input) ? null : Number(input) - Number(r.systemNetAmount)
    const matchedLocal = diff != null && Math.abs(diff) < EPS
    const localStatus = diff == null ? 'MISSING' : matchedLocal ? 'MATCHED' : 'MISMATCH'
    return { ...r, diff, matchedLocal, localStatus }
  })
})

function formatNum(n) {
  return Number(n).toLocaleString(undefined, { maximumFractionDigits: 8 })
}

function statusTagType(s) {
  if (s === 'MATCHED') return 'success'
  if (s === 'MISMATCH') return 'danger'
  return 'info'
}

function statusText(s) {
  if (s === 'MATCHED') return '一致'
  if (s === 'MISMATCH') return '差异'
  return '未录入'
}

function rowClassName({ row }) {
  if (row.localStatus === 'MISMATCH') return 'row-mismatch'
  if (row.localStatus === 'MISSING') return 'row-missing'
  return ''
}

async function loadRuns() {
  const { data: list } = await api.get('/netting-runs')
  runs.value = list
  return list
}

async function loadReconciliation(runId) {
  if (!runId) {
    data.value = null
    return
  }
  loading.value = true
  try {
    const { data: recon } = await api.get(`/reconciliations/${runId}`)
    data.value = recon
    const next = {}
    for (const r of recon.rows) {
      next[r.memberId] = r.receiptNetAmount == null ? null : Number(r.receiptNetAmount)
    }
    inputs.value = next
  } finally {
    loading.value = false
  }
}

async function loadAll() {
  const list = await loadRuns()
  if (selectedRunId.value && list.some((r) => r.runId === selectedRunId.value)) {
    await loadReconciliation(selectedRunId.value)
  } else if (list.length > 0) {
    const preferred = pickDefaultRun(list)
    selectedRunId.value = preferred
    await loadReconciliation(preferred)
  } else {
    data.value = null
  }
}

function pickDefaultRun(list) {
  const completed = list.filter((r) => r.status === 'COMPLETED')
  return (completed[0] || list[0]).runId
}

function onSelectRun(runId) {
  loadReconciliation(runId)
}

function fillFromSystem() {
  if (!data.value) return
  const next = { ...inputs.value }
  for (const r of data.value.rows) {
    next[r.memberId] = Number(r.systemNetAmount)
  }
  inputs.value = next
}

async function save() {
  if (!data.value) return
  const items = mergedRows.value
    .map((r) => ({ memberId: r.memberId, netAmount: inputs.value[r.memberId] }))
    .filter((x) => x.netAmount != null && !Number.isNaN(x.netAmount))
  if (items.length === 0) {
    ElMessage.warning('请先录入至少一条回执净额')
    return
  }
  saving.value = true
  try {
    const { data: recon } = await api.put(
      `/reconciliations/${selectedRunId.value}/receipts`,
      { items }
    )
    data.value = recon
    const next = {}
    for (const r of recon.rows) {
      next[r.memberId] = r.receiptNetAmount == null ? null : Number(r.receiptNetAmount)
    }
    inputs.value = next
    if (recon.allMatched) {
      ElMessage.success('保存成功，全部会员回执与系统净头寸一致')
    } else {
      ElMessage.warning(`保存成功，仍有 ${recon.mismatchCount} 条差异、${recon.missingCount} 条未录入`)
    }
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const list = await loadRuns()
  const queryRun = route.query.run
  if (queryRun && list.some((r) => r.runId === queryRun)) {
    selectedRunId.value = queryRun
  } else if (list.length > 0) {
    selectedRunId.value = pickDefaultRun(list)
  }
  if (selectedRunId.value) {
    await loadReconciliation(selectedRunId.value)
  }
})
</script>

<style scoped>
.summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.stat .label {
  color: var(--muted);
  font-size: 13px;
}
.stat .value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 700;
}
.stat.ok .value {
  color: #16a34a;
}
.stat.bad .value {
  color: #dc2626;
}
.stat.warn .value {
  color: #d97706;
}
.muted {
  color: var(--muted);
}
.diff-bad {
  color: #dc2626;
  font-weight: 700;
}
:deep(.el-table .row-mismatch td.el-table__cell) {
  background: #fef0f0 !important;
}
:deep(.el-table .row-missing td.el-table__cell) {
  background: #fdf6ec;
}
@media (max-width: 900px) {
  .summary {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
