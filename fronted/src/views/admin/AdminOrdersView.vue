<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../services/api'
import { normalizeOrder } from '../../services/normalizers'

const orders = ref([])
const disputes = ref([])
const loading = ref(false)
const disputeLoading = ref(false)
const filters = reactive({
  status: '',
  mode: '',
  time: '',
})

const filteredOrders = computed(() =>
  orders.value.filter((order) => {
    const statusMatched = !filters.status || order.status === filters.status
    const modeMatched = !filters.mode || order.tradeMode === filters.mode
    const timeMatched =
      !Array.isArray(filters.time) ||
      filters.time.length !== 2 ||
      (new Date(order.createdAt) >= new Date(filters.time[0]) &&
        new Date(order.createdAt) <= new Date(filters.time[1]))
    return statusMatched && modeMatched && timeMatched
  }),
)

function normalizeDispute(row = {}) {
  return {
    id: row.disputeId || row.id,
    disputeNo: row.disputeNo || '',
    orderId: row.orderId || '',
    applicantId: row.applicantId || '',
    reason: row.reason || '',
    status: row.status || 'PENDING',
    amount: row.amount || '',
    createdAt: String(row.createdAt || '').replace('T', ' ').slice(0, 16),
    resultRemark: row.resultRemark || '',
  }
}

async function loadOrders() {
  loading.value = true
  try {
    const response = await adminApi.orders({ page: 1, pageSize: 100 })
    orders.value = (response.data?.list || []).map(normalizeOrder)
  } catch (error) {
    orders.value = []
    ElMessage.error(error.message || 'Order list load failed')
  } finally {
    loading.value = false
  }
}

async function loadDisputes() {
  disputeLoading.value = true
  try {
    const response = await adminApi.disputes({ page: 1, pageSize: 100 })
    disputes.value = (response.data?.list || []).map(normalizeDispute)
  } catch (error) {
    disputes.value = []
    ElMessage.error(error.message || 'Dispute list load failed')
  } finally {
    disputeLoading.value = false
  }
}

function resolveDispute(row, result) {
  ElMessageBox.confirm(`Confirm dispute ${row.id}: ${result}?`, 'Resolve dispute', {
    confirmButtonText: 'Confirm',
    cancelButtonText: 'Cancel',
    type: result === 'REFUND' ? 'warning' : 'info',
  }).then(async () => {
    try {
      await adminApi.resolveDispute(row.id, { result, remark: result })
      ElMessage.success('Dispute resolved')
      loadDisputes()
    } catch (error) {
      ElMessage.error(error.message || 'Dispute resolve failed')
    }
  }).catch(() => {})
}

function orderStatusType(status) {
  if (status === 'COMPLETED' || status === 'PAID') return 'success'
  if (status === 'CANCELLED') return 'info'
  if (status === 'PAYING') return 'warning'
  return 'primary'
}

onMounted(() => {
  loadOrders()
  loadDisputes()
})
</script>

<template>
  <div class="admin-page">
    <el-card shadow="never" class="admin-filter-card">
      <el-form label-position="top">
        <div class="admin-filter-grid">
          <el-form-item label="Order Status">
            <el-select v-model="filters.status" clearable placeholder="All status">
              <el-option label="Pending" value="PENDING" />
              <el-option label="Accepted" value="ACCEPTED" />
              <el-option label="Paying" value="PAYING" />
              <el-option label="Paid" value="PAID" />
              <el-option label="Completed" value="COMPLETED" />
              <el-option label="Cancelled" value="CANCELLED" />
            </el-select>
          </el-form-item>
          <el-form-item label="Trade Mode">
            <el-select v-model="filters.mode" clearable placeholder="All modes">
              <el-option label="Offline" value="OFFLINE" />
              <el-option label="Escrow" value="ESCROW" />
            </el-select>
          </el-form-item>
          <el-form-item label="Date Range">
            <el-date-picker v-model="filters.time" type="daterange" start-placeholder="Start" end-placeholder="End" />
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-tabs>
        <el-tab-pane label="Orders">
          <el-table v-loading="loading" :data="filteredOrders" stripe>
            <el-table-column prop="orderNo" label="Order No" min-width="170" />
            <el-table-column prop="product.title" label="Item" min-width="220" />
            <el-table-column prop="buyerName" label="Buyer" min-width="120" />
            <el-table-column prop="sellerName" label="Seller" min-width="120" />
            <el-table-column prop="status" label="Status" min-width="110">
              <template #default="{ row }">
                <el-tag :type="orderStatusType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="tradeMode" label="Mode" min-width="110" />
            <el-table-column prop="amount" label="Amount" min-width="100" />
            <el-table-column prop="createdAt" label="Created At" min-width="170" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="Disputes">
          <el-table v-loading="disputeLoading" :data="disputes" stripe>
            <el-table-column prop="disputeNo" label="Dispute No" min-width="160" />
            <el-table-column prop="orderId" label="Order ID" min-width="120" />
            <el-table-column prop="applicantId" label="Applicant" min-width="110" />
            <el-table-column prop="reason" label="Reason" min-width="260" />
            <el-table-column prop="status" label="Status" min-width="110" />
            <el-table-column prop="resultRemark" label="Result" min-width="180" />
            <el-table-column label="Actions" fixed="right" width="220">
              <template #default="{ row }">
                <el-button link type="success" :disabled="row.status === 'RESOLVED'" @click="resolveDispute(row, 'REFUND')">
                  Refund
                </el-button>
                <el-button link type="danger" :disabled="row.status === 'RESOLVED'" @click="resolveDispute(row, 'REJECT')">
                  Reject
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
