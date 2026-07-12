<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../services/api'

const categories = ref([])
const loading = ref(false)
const saving = ref(false)
const categoryDialog = ref(false)
const editingCategory = ref(null)
const tagInputs = reactive({})
const categoryForm = reactive({
  name: '',
  tags: '',
})

function normalizeCategory(row = {}) {
  return {
    id: row.categoryId || row.id,
    name: row.name || '',
    tags: Array.isArray(row.tags) ? row.tags : [],
    productCount: Number(row.productCount) || 0,
    sortOrder: Number(row.sortOrder) || 0,
  }
}

async function loadCategories() {
  loading.value = true
  try {
    const response = await adminApi.categories()
    categories.value = (response.data || []).map(normalizeCategory)
  } catch (error) {
    categories.value = []
    ElMessage.error(error.message || 'Category list load failed')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingCategory.value = null
  categoryForm.name = ''
  categoryForm.tags = ''
  categoryDialog.value = true
}

function editCategory(category) {
  editingCategory.value = category
  categoryForm.name = category.name
  categoryForm.tags = category.tags.join(',')
  categoryDialog.value = true
}

async function saveCategory() {
  if (!categoryForm.name.trim()) {
    ElMessage.warning('Category name is required')
    return
  }
  saving.value = true
  const payload = {
    name: categoryForm.name.trim(),
    tags: categoryForm.tags.split(/[,，、;；]/).map((tag) => tag.trim()).filter(Boolean),
  }
  try {
    if (editingCategory.value) {
      await adminApi.updateCategory(editingCategory.value.id, payload)
    } else {
      await adminApi.createCategory(payload)
    }
    ElMessage.success('Category saved')
    categoryDialog.value = false
    loadCategories()
  } catch (error) {
    ElMessage.error(error.message || 'Category save failed')
  } finally {
    saving.value = false
  }
}

async function addTag(category) {
  const tag = (tagInputs[category.id] || '').trim()
  if (!tag) {
    ElMessage.warning('Tag name is required')
    return
  }
  const tags = [...new Set([...category.tags, tag])]
  try {
    await adminApi.updateCategory(category.id, { name: category.name, tags })
    tagInputs[category.id] = ''
    ElMessage.success('Tag added')
    loadCategories()
  } catch (error) {
    ElMessage.error(error.message || 'Tag add failed')
  }
}

async function closeTag(category, tag) {
  const tags = category.tags.filter((item) => item !== tag)
  try {
    await adminApi.updateCategory(category.id, { name: category.name, tags })
    ElMessage.success('Tag deleted')
    loadCategories()
  } catch (error) {
    ElMessage.error(error.message || 'Tag delete failed')
  }
}

function deleteCategory(category) {
  ElMessageBox.confirm(`Delete category "${category.name}"? Categories with items will be disabled.`, 'Delete category', {
    confirmButtonText: 'Delete',
    cancelButtonText: 'Cancel',
    type: 'warning',
  }).then(async () => {
    try {
      await adminApi.deleteCategory(category.id)
      ElMessage.success('Category deleted')
      loadCategories()
    } catch (error) {
      ElMessage.error(error.message || 'Category delete failed')
    }
  }).catch(() => {})
}

onMounted(loadCategories)
</script>

<template>
  <div class="admin-page">
    <el-card shadow="never">
      <template #header>
        <div class="admin-card-header">
          <strong>Category Management</strong>
          <el-button type="primary" @click="openCreate">New Category</el-button>
        </div>
      </template>

      <div v-loading="loading" class="category-admin-grid">
        <el-card v-for="category in categories" :key="category.id" shadow="hover" class="category-admin-card">
          <div class="category-title-line">
            <h3>{{ category.name }}</h3>
            <el-tag>{{ category.productCount }} items</el-tag>
          </div>
          <div class="tag-stack">
            <el-tag v-for="tag in category.tags" :key="tag" closable @close="closeTag(category, tag)">
              {{ tag }}
            </el-tag>
          </div>
          <div class="tag-add-row">
            <el-input v-model="tagInputs[category.id]" placeholder="New tag" />
            <el-button @click="addTag(category)">Add</el-button>
          </div>
          <div class="category-actions">
            <el-button type="primary" plain @click="editCategory(category)">Edit</el-button>
            <el-button type="danger" plain @click="deleteCategory(category)">Delete</el-button>
          </div>
        </el-card>
      </div>
    </el-card>

    <el-dialog v-model="categoryDialog" :title="editingCategory ? 'Edit Category' : 'New Category'" width="420">
      <el-form label-position="top">
        <el-form-item label="Category Name">
          <el-input v-model="categoryForm.name" placeholder="Category name" />
        </el-form-item>
        <el-form-item label="Tags">
          <el-input v-model="categoryForm.tags" placeholder="Separated by comma" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialog = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="saveCategory">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>
