<template>
  <div class="page">
    <h2 class="page-title">回执对账</h2>
    <p class="page-desc">录入各会员回执净额，与系统净头寸逐会员比对，差异会员自动标出</p>

    <div class="card-panel">
      <div class="toolbar">
        <el-select
          v-model="runId"
          placeholder="选择轧差批次"
          style="width: 460px"
          filterable
          @change="loadReconciliation"
        >
          <el-option
            v-for="r in runs"
            :key="r.runId"
            :value="r.runId"
            :label="`${r.settleDate} · ${r.currency} · ${r.status} · ${r.runId.slice(0, 8)}…`"
          />
        </el-select>
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
        <template v-if="recon">
          <el-tag :type="summaryType" size="large">{{ summaryText }}</el-tag>
        </template>
        <span v-if="!auth.isOperator" class="hint">当前为只读账号，仅可查看；录入回执需操作员登录</span>
      </div>
    </div>

    <div class="card-panel" style="margin-top: 16px" v-loading="loading">
      <template v-if="recon">
        <el-table :data="recon.rows" :row-class-name="rowClass" stripe>
          <el-table-column label="会员" min-width="230">
            <template #default="{ row }">
              <span class="mono">{{ row.memberId }}</span>
              <div>{{ nameOf(row.memberId) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="系统净头寸" min-width="150">
            <template #default="{ row }">{{ row.systemNetAmount ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="回执净额" min-width="200">
            <template #default="{ row }">
              <el-input-number
                v-if="auth.isOperator"
                v-model="inputs[row.memberId]"
                :precision="2"
                :controls="false"
                placeholder="录入回执净额"
                style="width: 170px"
              />
              <span v-else>{{ row.reportedAmount ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="差异（回执-系统）" min-width="150">
            <template #default="{ row }">
              <span :class="{ 'diff-text': row.status === 'DIFF' }">{{ row.difference ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="auth.isOperator" label="操作" width="190">
            <template #default="{ row }">
              <el-button
                size="small"
                type="primary"
                :loading="savingId === row.memberId"
                @click="save(row)"
              >保存回执</el-button>
              <el-button
                size="small"
                link
                type="danger"
                :disabled="row.reportedAmount == null"
                @click="clear(row)"
              >清除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else description="请选择轧差批次" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const runs = ref([])
const runId = ref('')
const recon = ref(null)
const inputs = ref({})
const memberMap = ref({})
const loading = ref(false)
const savingId = ref('')

const summaryText = computed(() => {
  if (!recon.value) return ''
  const parts = [`相符 ${recon.value.matchedCount}`]
  if (recon.value.diffCount > 0) parts.push(`差异 ${recon.value.diffCount}`)
  if (recon.value.missingCount > 0) parts.push(`未回执 ${recon.value.missingCount}`)
  if (recon.value.extraCount > 0) parts.push(`多出回执 ${recon.value.extraCount}`)
  return parts.join(' · ')
})

const summaryType = computed(() => {
  if (!recon.value) return 'info'
  if (recon.value.diffCount > 0 || recon.value.extraCount > 0) return 'danger'
  if (recon.value.missingCount > 0) return 'warning'
  return 'success'
})

function nameOf(id) {
  return memberMap.value[id] || ''
}

function statusType(s) {
  if (s === 'MATCHED') return 'success'
  if (s === 'DIFF' || s === 'EXTRA_RECEIPT') return 'danger'
  return 'warning'
}

function statusLabel(s) {
  return {
    MATCHED: '相符',
    DIFF: '差异',
    MISSING_RECEIPT: '未回执',
    EXTRA_RECEIPT: '多出回执'
  }[s] || s
}

function rowClass({ row }) {
  if (row.status === 'DIFF' || row.status === 'EXTRA_RECEIPT') return 'row-diff'
  if (row.status === 'MISSING_RECEIPT') return 'row-missing'
  return ''
}

function applyRecon(data) {
  recon.value = data
  const next = {}
  for (const row of data.rows) {
    next[row.memberId] = row.reportedAmount == null ? null : Number(row.reportedAmount)
  }
  inputs.value = next
}

async function loadAll() {
  loading.value = true
  try {
    const [r, m] = await Promise.all([api.get('/netting-runs'), api.get('/members')])
    runs.value = r.data
    memberMap.value = Object.fromEntries(m.data.map((x) => [x.memberId, x.name]))
    if (!runId.value || !runs.value.some((x) => x.runId === runId.value)) {
      const completed = runs.value.find((x) => x.status === 'COMPLETED')
      runId.value = (completed || runs.value[0] || {}).runId || ''
    }
    if (runId.value) {
      await loadReconciliation()
    } else {
      recon.value = null
    }
  } finally {
    loading.value = false
  }
}

async function loadReconciliation() {
  if (!runId.value) return
  loading.value = true
  try {
    const { data } = await api.get(`/netting-runs/${runId.value}/reconciliation`)
    applyRecon(data)
  } finally {
    loading.value = false
  }
}

async function save(row) {
  const amount = inputs.value[row.memberId]
  if (amount == null || Number.isNaN(Number(amount))) {
    ElMessage.warning('请先录入回执净额')
    return
  }
  savingId.value = row.memberId
  try {
    const { data } = await api.post(`/netting-runs/${runId.value}/receipts`, {
      memberId: row.memberId,
      reportedAmount: amount
    })
    applyRecon(data)
    const updated = data.rows.find((x) => x.memberId === row.memberId)
    if (updated?.status === 'MATCHED') {
      ElMessage.success('回执已保存，与系统净头寸相符')
    } else if (updated?.status === 'DIFF') {
      ElMessage.warning(`回执已保存，差异 ${updated.difference}`)
    } else {
      ElMessage.success('回执已保存')
    }
  } finally {
    savingId.value = ''
  }
}

async function clear(row) {
  savingId.value = row.memberId
  try {
    const { data } = await api.delete(`/netting-runs/${runId.value}/receipts/${row.memberId}`)
    applyRecon(data)
    ElMessage.success('回执已清除')
  } finally {
    savingId.value = ''
  }
}

onMounted(loadAll)
</script>

<style scoped>
.hint {
  color: var(--muted);
  font-size: 13px;
}
.diff-text {
  color: #c45656;
  font-weight: 700;
}
:deep(.el-table .row-diff) {
  --el-table-tr-bg-color: #fef0f0;
}
:deep(.el-table .row-missing) {
  --el-table-tr-bg-color: #fdf6ec;
}
</style>
