<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { campuses } from '../../data/options'
import { adminApi } from '../../services/api'

const noticeDialog = ref(false)
const editingNotice = ref(null)
const notices = ref([])
const loading = ref(false)
const saving = ref(false)
const noticeForm = reactive({
  title: '',
  scope: '全平台',
  content: '',
})

function normalizeNotice(row = {}) {
  const scope = row.scopeType === 'ALL' ? '全平台' : row.campus || '指定校区'
  return {
    id: row.noticeId || row.id,
    title: row.title || '',
    content: row.content || '',
    scope,
    scopeType: row.scopeType || 'ALL',
    campus: row.campus || '',
    status: row.status === 'PUBLISHED' ? '已发布' : '草稿',
    statusCode: row.status || 'DRAFT',
    createdAt: String(row.createdAt || '').replace('T', ' ').slice(0, 16),
  }
}

async function loadNotices() {
  loading.value = true
  try {
    const response = await adminApi.notices()
    notices.value = (response.data?.list || []).map(normalizeNotice)
  } catch (error) {
    notices.value = []
    ElMessage.error(error.message || '公告列表加载失败')
  } finally {
    loading.value = false
  }
}

function resetNoticeForm() {
  editingNotice.value = null
  noticeForm.title = ''
  noticeForm.scope = '全平台'
  noticeForm.content = ''
}

function openCreate() {
  resetNoticeForm()
  noticeDialog.value = true
}

function editNotice(row) {
  editingNotice.value = row
  noticeForm.title = row.title
  noticeForm.scope = row.scope
  noticeForm.content = row.content
  noticeDialog.value = true
}

function deleteNotice(row) {
  ElMessageBox.confirm(`确认删除公告「${row.title}」？`, '删除公告', {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await adminApi.deleteNotice(row.id)
      ElMessage.success('公告已删除')
      loadNotices()
    } catch (error) {
      ElMessage.error(error.message || '公告删除失败')
    }
  }).catch(() => {})
}

async function saveNotice(status) {
  if (!noticeForm.title.trim()) {
    ElMessage.warning('请输入公告标题')
    return
  }
  if (!noticeForm.content.trim()) {
    ElMessage.warning('请输入公告内容')
    return
  }

  saving.value = true
  try {
    const payload = {
      title: noticeForm.title.trim(),
      content: noticeForm.content.trim(),
      scope: noticeForm.scope,
      status: status === 'publish' ? 'PUBLISHED' : 'DRAFT',
      popupEnabled: status === 'publish',
    }
    if (editingNotice.value) {
      await adminApi.updateNotice(editingNotice.value.id, payload)
    } else {
      await adminApi.createNotice(payload)
    }
    noticeDialog.value = false
    ElMessage.success(status === 'draft' ? '公告草稿已保存' : '公告已发布')
    resetNoticeForm()
    loadNotices()
  } catch (error) {
    ElMessage.error(error.message || '公告保存失败')
  } finally {
    saving.value = false
  }
}

function statusType(statusCode) {
  return statusCode === 'PUBLISHED' ? 'success' : 'warning'
}

onMounted(loadNotices)
</script>

<template>
  <div class="admin-page">
    <el-card shadow="never">
      <template #header>
        <div class="admin-card-header">
          <strong>平台公告管理</strong>
          <el-button type="primary" @click="openCreate">新增公告</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="notices" stripe>
        <el-table-column prop="title" label="公告标题" min-width="260" />
        <el-table-column prop="scope" label="推送范围" min-width="130">
          <template #default="{ row }">
            <el-tag :type="row.scope === '全平台' ? 'danger' : 'warning'">{{ row.scope }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.statusCode)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column label="操作" fixed="right" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="editNotice(row)">编辑</el-button>
            <el-button link type="danger" @click="deleteNotice(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="noticeDialog" title="新增系统公告" width="560">
      <el-form label-position="top">
        <el-form-item label="公告标题">
          <el-input v-model="noticeForm.title" placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="弹窗推送范围">
          <el-select v-model="noticeForm.scope">
            <el-option label="全平台" value="全平台" />
            <el-option v-for="campus in campuses" :key="campus" :label="campus" :value="campus" />
          </el-select>
        </el-form-item>
        <el-form-item label="公告内容">
          <el-input v-model="noticeForm.content" type="textarea" :rows="5" placeholder="请输入公告内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :loading="saving" @click="saveNotice('draft')">保存草稿</el-button>
        <el-button type="primary" :loading="saving" @click="saveNotice('publish')">发布公告</el-button>
      </template>
    </el-dialog>
  </div>
</template>
