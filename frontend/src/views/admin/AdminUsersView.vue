<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../services/api'

const users = ref([])
const loading = ref(false)
const detailDialog = ref(false)
const currentUser = ref({})

const pendingCount = computed(() => users.value.filter((user) => user.verifiedStatus !== 'VERIFIED').length)

function normalizeUser(row = {}) {
  return {
    userId: row.userId,
    studentNo: row.studentNo || '',
    name: row.realName || row.nickname || '',
    nickname: row.nickname || '',
    department: row.department || '',
    campus: row.campus || '',
    verifiedStatus: row.verifiedStatus || 'UNVERIFIED',
    creditScore: row.creditScore ?? 100,
    phone: row.phone || '',
    status: row.status || 'NORMAL',
    createdAt: String(row.createdAt || '').replace('T', ' ').slice(0, 16),
  }
}

async function loadUsers() {
  loading.value = true
  try {
    const response = await adminApi.users({ page: 1, pageSize: 100 })
    users.value = (response.data?.list || []).map(normalizeUser)
  } catch (error) {
    users.value = []
    ElMessage.error(error.message || 'User list load failed')
  } finally {
    loading.value = false
  }
}

function openDetail(user) {
  currentUser.value = user
  detailDialog.value = true
}

function exportUsers() {
  const rows = users.value.map((user) => `${user.userId},${user.studentNo},${user.name},${user.status}`).join('\n')
  const blob = new Blob([`id,studentNo,name,status\n${rows}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'users.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function batchAudit() {
  try {
    const response = await adminApi.verifyPendingUsers()
    ElMessage.success(`Verified ${response.data?.updated || 0} users`)
    loadUsers()
  } catch (error) {
    ElMessage.error(error.message || 'Batch verify failed')
  }
}

async function auditUser(user) {
  try {
    await adminApi.verifyUser(user.userId)
    ElMessage.success('User verified')
    loadUsers()
  } catch (error) {
    ElMessage.error(error.message || 'Verify failed')
  }
}

function toggleUserStatus(user) {
  const disabling = user.status === 'NORMAL'
  ElMessageBox.confirm(`Confirm ${disabling ? 'disable' : 'enable'} this user?`, 'User status', {
    confirmButtonText: 'Confirm',
    cancelButtonText: 'Cancel',
    type: 'warning',
  }).then(async () => {
    try {
      if (disabling) {
        await adminApi.disableUser(user.userId)
      } else {
        await adminApi.enableUser(user.userId)
      }
      ElMessage.success('User status updated')
      loadUsers()
    } catch (error) {
      ElMessage.error(error.message || 'Status update failed')
    }
  }).catch(() => {})
}

onMounted(loadUsers)
</script>

<template>
  <div class="admin-page">
    <el-card shadow="never">
      <template #header>
        <div class="admin-card-header">
          <strong>User Management</strong>
          <div>
            <el-tag type="warning">Pending verify: {{ pendingCount }}</el-tag>
            <el-button @click="exportUsers">Export</el-button>
            <el-button type="primary" @click="batchAudit">Verify pending</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="users" stripe>
        <el-table-column prop="studentNo" label="Student No" min-width="120" />
        <el-table-column prop="name" label="Name" min-width="120" />
        <el-table-column prop="department" label="Department" min-width="150" />
        <el-table-column prop="campus" label="Campus" min-width="120" />
        <el-table-column prop="verifiedStatus" label="Verify" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.verifiedStatus === 'VERIFIED' ? 'success' : 'warning'">
              {{ row.verifiedStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creditScore" label="Credit" min-width="90" />
        <el-table-column prop="phone" label="Phone" min-width="130" />
        <el-table-column prop="createdAt" label="Created At" min-width="170" />
        <el-table-column prop="status" label="Status" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'NORMAL' ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" fixed="right" width="250">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">Detail</el-button>
            <el-button link type="warning" @click="auditUser(row)">Verify</el-button>
            <el-button link :type="row.status === 'NORMAL' ? 'danger' : 'success'" @click="toggleUserStatus(row)">
              {{ row.status === 'NORMAL' ? 'Disable' : 'Enable' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailDialog" title="User Detail" width="520">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="Student No">{{ currentUser.studentNo }}</el-descriptions-item>
        <el-descriptions-item label="Name">{{ currentUser.name }}</el-descriptions-item>
        <el-descriptions-item label="Nickname">{{ currentUser.nickname }}</el-descriptions-item>
        <el-descriptions-item label="Department">{{ currentUser.department }}</el-descriptions-item>
        <el-descriptions-item label="Campus">{{ currentUser.campus }}</el-descriptions-item>
        <el-descriptions-item label="Verify">{{ currentUser.verifiedStatus }}</el-descriptions-item>
        <el-descriptions-item label="Credit">{{ currentUser.creditScore }}</el-descriptions-item>
        <el-descriptions-item label="Phone">{{ currentUser.phone }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>
