<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../services/api'

const reports = ref([])
const loading = ref(false)
const pendingCount = computed(() => reports.value.filter((report) => report.status === 'PENDING').length)

function normalizeReport(row = {}) {
  return {
    id: row.reportId || row.id,
    reporter: row.reporterName || (row.reporterId ? `User ${row.reporterId}` : ''),
    target: row.targetName || `${row.targetType || ''} ${row.targetId || ''}`.trim(),
    targetType: row.targetType || '',
    targetId: row.targetId,
    type: row.reportType || '',
    content: row.content || '',
    status: row.status || 'PENDING',
    resultRemark: row.resultRemark || '',
    createdAt: String(row.createdAt || '').replace('T', ' ').slice(0, 16),
  }
}

async function loadReports() {
  loading.value = true
  try {
    const response = await adminApi.reports({ page: 1, pageSize: 100 })
    reports.value = (response.data?.list || []).map(normalizeReport)
  } catch (error) {
    reports.value = []
    ElMessage.error(error.message || 'Report list load failed')
  } finally {
    loading.value = false
  }
}

function handleReport(row, action) {
  const labels = {
    APPROVE: 'approve report',
    OFF_SHELF: 'approve and remove item',
    OFF_SHELF_AND_PENALIZE: 'approve, remove item and deduct credit',
    REJECT: 'reject report',
  }
  ElMessageBox.confirm(`Confirm ${labels[action]} for report ${row.id}?`, 'Report review', {
    confirmButtonText: 'Confirm',
    cancelButtonText: 'Cancel',
    type: action === 'REJECT' ? 'info' : 'warning',
  }).then(async () => {
    try {
      if (action === 'REJECT') {
        await adminApi.rejectReport(row.id, { remark: 'Report rejected' })
      } else {
        await adminApi.approveReport(row.id, {
          action,
          remark: labels[action],
          creditDeduction: 10,
        })
      }
      ElMessage.success('Report handled')
      loadReports()
    } catch (error) {
      ElMessage.error(error.message || 'Report handle failed')
    }
  }).catch(() => {})
}

function reportStatusType(status) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'info'
  return 'warning'
}

onMounted(loadReports)
</script>

<template>
  <div class="admin-page">
    <el-card shadow="never">
      <template #header>
        <div class="admin-card-header">
          <strong>Report Review</strong>
          <el-tag type="danger">Pending {{ pendingCount }}</el-tag>
        </div>
      </template>

      <el-table v-loading="loading" :data="reports" stripe>
        <el-table-column prop="id" label="Report ID" min-width="110" />
        <el-table-column prop="reporter" label="Reporter" min-width="130" />
        <el-table-column prop="target" label="Target" min-width="220" />
        <el-table-column prop="type" label="Type" min-width="130" />
        <el-table-column prop="content" label="Content" min-width="300" />
        <el-table-column prop="status" label="Status" min-width="110">
          <template #default="{ row }">
            <el-tag :type="reportStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resultRemark" label="Result" min-width="180" />
        <el-table-column label="Actions" fixed="right" width="330">
          <template #default="{ row }">
            <el-button link type="danger" :disabled="row.status !== 'PENDING'" @click="handleReport(row, 'APPROVE')">
              Approve
            </el-button>
            <el-button link type="warning" :disabled="row.status !== 'PENDING' || row.targetType !== 'ITEM'" @click="handleReport(row, 'OFF_SHELF')">
              Remove Item
            </el-button>
            <el-button link type="warning" :disabled="row.status !== 'PENDING' || row.targetType !== 'ITEM'" @click="handleReport(row, 'OFF_SHELF_AND_PENALIZE')">
              Remove + Deduct
            </el-button>
            <el-button link :disabled="row.status !== 'PENDING'" @click="handleReport(row, 'REJECT')">
              Reject
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
