<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../services/api'

const loading = ref(false)
const saving = ref(false)
const documents = ref([])
const editingId = ref(null)
const form = reactive({ title: '', content: '', documentType: 'POLICY', sourceRef: '' })

function resetForm() {
  editingId.value = null
  form.title = ''
  form.content = ''
  form.documentType = 'POLICY'
  form.sourceRef = ''
}

async function loadDocuments() {
  loading.value = true
  try {
    const response = await adminApi.knowledgeDocuments()
    documents.value = response.data || response || []
  } catch (error) {
    documents.value = []
    ElMessage.error(error.message || '知识库加载失败')
  } finally {
    loading.value = false
  }
}

function edit(row) {
  editingId.value = row.documentId
  form.title = row.title || ''
  form.content = row.content || ''
  form.documentType = row.documentType || 'POLICY'
  form.sourceRef = row.sourceRef || ''
}

async function save() {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('请填写知识标题和内容')
    return
  }
  saving.value = true
  try {
    const payload = { ...form, title: form.title.trim(), content: form.content.trim() }
    if (editingId.value) await adminApi.updateKnowledgeDocument(editingId.value, payload)
    else await adminApi.createKnowledgeDocument(payload)
    ElMessage.success('知识文档已保存，等待索引')
    resetForm()
    loadDocuments()
  } catch (error) {
    ElMessage.error(error.message || '保存知识文档失败')
  } finally {
    saving.value = false
  }
}

async function publish(row) {
  try {
    await adminApi.publishKnowledgeDocument(row.documentId)
    ElMessage.success('已发布并加入索引队列')
    loadDocuments()
  } catch (error) {
    ElMessage.error(error.message || '发布失败')
  }
}

async function reindex() {
  try {
    const response = await adminApi.reindexKnowledge()
    ElMessage.success(`已加入 ${response.data?.queued ?? 0} 条重建任务`)
  } catch (error) {
    ElMessage.error(error.message || '重建索引失败')
  }
}

onMounted(loadDocuments)
</script>

<template>
  <div class="admin-page">
    <el-card shadow="never">
      <template #header>
        <div class="admin-card-header">
          <strong>Agent 知识库</strong>
          <div>
            <el-button @click="reindex">重建已发布索引</el-button>
            <el-button type="primary" @click="resetForm">新增知识</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="documents" stripe>
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="documentType" label="类型" width="100" />
        <el-table-column prop="versionNo" label="版本" width="80" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'warning'">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
        <el-table-column label="操作" fixed="right" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="edit(row)">编辑</el-button>
            <el-button v-if="row.status !== 'PUBLISHED'" link type="success" @click="publish(row)">发布</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="mt-16" shadow="never">
      <template #header><strong>{{ editingId ? '编辑知识文档' : '新增知识文档' }}</strong></template>
      <el-form label-position="top">
        <el-form-item label="标题"><el-input v-model="form.title" maxlength="255" show-word-limit /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.documentType"><el-radio value="POLICY">平台规则</el-radio><el-radio value="FAQ">FAQ</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="来源标识（可选）"><el-input v-model="form.sourceRef" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="8" maxlength="12000" show-word-limit /></el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存草稿</el-button>
      </el-form>
    </el-card>
  </div>
</template>
