<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { campuses, conditions } from '../../data/mock'
import { adminApi } from '../../services/api'
import { normalizeItemPage } from '../../services/normalizers'

const selectedRows = ref([])
const products = ref([])
const users = ref([])
const categoryOptions = ref([])
const loading = ref(false)
const creating = ref(false)
const createDialog = ref(false)
const filters = reactive({
  category: '',
  campus: '',
  date: '',
})
const createForm = reactive({
  sellerId: '',
  title: '',
  description: '',
  price: 0,
  originalPrice: 0,
  condition: '',
  category: '',
  campus: '',
  dormitory: '',
  tradePlace: '',
  tradeModes: ['面交'],
  status: '上架',
  swapSupported: false,
  imageUrl: '',
})

const filteredProducts = computed(() =>
  products.value.filter((product) => {
    const categoryMatched = !filters.category || product.category === filters.category
    const campusMatched = !filters.campus || product.campus === filters.campus
    const dateMatched =
      !Array.isArray(filters.date) ||
      filters.date.length !== 2 ||
      (new Date(product.publishedAt || product.date) >= new Date(filters.date[0]) &&
        new Date(product.publishedAt || product.date) <= new Date(filters.date[1]))
    return categoryMatched && campusMatched && dateMatched
  }),
)

async function loadItems() {
  loading.value = true
  try {
    const response = await adminApi.items()
    products.value = normalizeItemPage(response).list.map((item) => ({
      ...item,
      publishedAt: item.date,
    }))
  } catch (error) {
    ElMessage.error(error.message || '商品列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadUsers() {
  try {
    const response = await adminApi.users({ page: 1, pageSize: 100 })
    users.value = response.data?.list || []
  } catch (error) {
    users.value = []
    console.error(error)
  }
}

async function loadCategories() {
  try {
    const response = await adminApi.categories()
    categoryOptions.value = (response.data || []).map((category) => category.name).filter(Boolean)
  } catch (error) {
    categoryOptions.value = []
    console.error(error)
  }
}

function batchAudit() {
  ElMessage.success(`已审核 ${selectedRows.value.length || filteredProducts.value.length} 件商品`)
}

function batchRemove() {
  ElMessageBox.confirm('确认批量下架违规商品？', '批量下架', {
    confirmButtonText: '确认下架',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    const rows = selectedRows.value.length ? selectedRows.value : filteredProducts.value
    await Promise.all(rows.map((row) => adminApi.offShelfItem(row.id)))
    ElMessage.success(`已下架 ${rows.length} 件商品`)
    loadItems()
  }).catch(() => {})
}

function removeItem(row) {
  ElMessageBox.confirm(`确认下架「${row.title}」？`, '下架违规商品', {
    confirmButtonText: '确认下架',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    await adminApi.offShelfItem(row.id)
    ElMessage.success('商品已下架')
    loadItems()
  }).catch(() => {})
}

function deleteItem(row) {
  ElMessageBox.confirm(`确认删除「${row.title}」？删除后不可恢复。`, '删除商品', {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'error',
  }).then(async () => {
    await adminApi.deleteItem(row.id)
    ElMessage.success('商品已删除')
    loadItems()
  }).catch(() => {})
}

function auditItem(row) {
  ElMessage.success(`商品「${row.title}」已通过审核`)
}

function openCreateDialog() {
  if (!users.value.length) {
    ElMessage.warning('请先注册普通用户，再为该用户新增商品')
    loadUsers()
    return
  }
  createDialog.value = true
}

function resetCreateForm() {
  Object.assign(createForm, {
    sellerId: '',
    title: '',
    description: '',
    price: 0,
    originalPrice: 0,
    condition: '',
    category: '',
    campus: '',
    dormitory: '',
    tradePlace: '',
    tradeModes: ['面交'],
    status: '上架',
    swapSupported: false,
    imageUrl: '',
  })
}

function validateCreateForm() {
  if (!createForm.sellerId) return '请选择卖家账号'
  if (!createForm.title.trim()) return '请填写商品标题'
  if (!createForm.description.trim()) return '请填写商品描述'
  if (!createForm.price && createForm.price !== 0) return '请填写售价'
  if (!createForm.condition) return '请选择成色'
  if (!createForm.category) return '请选择分类'
  if (!createForm.campus) return '请选择校区'
  if (!createForm.tradeModes.length) return '至少选择一种交易模式'
  return ''
}

async function createItem() {
  const error = validateCreateForm()
  if (error) {
    ElMessage.warning(error)
    return
  }

  creating.value = true
  try {
    await adminApi.createItem({
      sellerId: createForm.sellerId,
      title: createForm.title.trim(),
      description: createForm.description.trim(),
      price: createForm.price,
      originalPrice: createForm.originalPrice,
      condition: createForm.condition,
      category: createForm.category,
      campus: createForm.campus,
      dormitory: createForm.dormitory.trim(),
      tradePlace: createForm.tradePlace.trim(),
      tradeModes: createForm.tradeModes,
      status: createForm.status,
      swapSupported: createForm.swapSupported,
      imageUrl: createForm.imageUrl.trim(),
    })
    ElMessage.success('商品已新增')
    createDialog.value = false
    resetCreateForm()
    loadItems()
  } catch (error) {
    ElMessage.error(error.message || '新增商品失败')
  } finally {
    creating.value = false
  }
}

function statusType(status) {
  if (status === 'ON_SALE' || status === '上架') return 'success'
  if (status === 'REMOVED' || status === '违规') return 'danger'
  if (status === 'SOLD') return 'info'
  return 'warning'
}

function statusText(status) {
  const map = {
    ON_SALE: '上架',
    REMOVED: '已下架',
    SOLD: '已售出',
    RESERVED: '已预约',
    DRAFT: '草稿',
  }
  return map[status] || status || '未知'
}

onMounted(() => {
  loadItems()
  loadUsers()
  loadCategories()
})
</script>

<template>
  <div class="admin-page">
    <el-card shadow="never" class="admin-filter-card">
      <el-form label-position="top">
        <div class="admin-filter-grid">
          <el-form-item label="分类">
            <el-select v-model="filters.category" clearable placeholder="全部分类">
              <el-option v-for="category in categoryOptions" :key="category" :label="category" :value="category" />
            </el-select>
          </el-form-item>
          <el-form-item label="校区">
            <el-select v-model="filters.campus" clearable placeholder="全部校区">
              <el-option v-for="campus in campuses" :key="campus" :label="campus" :value="campus" />
            </el-select>
          </el-form-item>
          <el-form-item label="发布时间">
            <el-date-picker v-model="filters.date" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" />
          </el-form-item>
          <el-form-item label="批量操作">
            <el-button type="primary" @click="batchAudit">批量审核</el-button>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="admin-card-header">
          <strong>商品列表</strong>
          <div>
            <el-button type="primary" @click="openCreateDialog">新增商品</el-button>
            <el-button type="danger" @click="batchRemove">批量下架违规商品</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="filteredProducts" stripe @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="title" label="商品标题" min-width="220" />
        <el-table-column prop="category" label="分类" min-width="110" />
        <el-table-column prop="seller" label="卖家" min-width="120" />
        <el-table-column prop="campus" label="发布校区" min-width="120" />
        <el-table-column prop="price" label="价格" min-width="100">
          <template #default="{ row }">￥{{ row.price }}</template>
        </el-table-column>
        <el-table-column prop="status" label="商品状态" min-width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishedAt" label="发布时间" min-width="170" />
        <el-table-column label="操作" fixed="right" width="220">
          <template #default="{ row }">
            <el-button link type="warning" @click="removeItem(row)">下架违规商品</el-button>
            <el-button link type="danger" @click="deleteItem(row)">删除商品</el-button>
            <el-button v-if="row.status === '违规'" link type="primary" @click="auditItem(row)">审核</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createDialog" title="后台新增商品" width="720">
      <el-form label-position="top">
        <div class="two-column-form">
          <el-form-item label="卖家账号">
            <el-select v-model="createForm.sellerId" filterable placeholder="选择普通用户卖家">
              <el-option
                v-for="user in users"
                :key="user.userId"
                :label="`${user.nickname || user.studentNo}（ID: ${user.userId}）`"
                :value="user.userId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="发布状态">
            <el-radio-group v-model="createForm.status">
              <el-radio-button label="上架" />
              <el-radio-button label="草稿" />
            </el-radio-group>
          </el-form-item>
          <el-form-item label="商品标题">
            <el-input v-model="createForm.title" maxlength="40" show-word-limit />
          </el-form-item>
          <el-form-item label="售价">
            <el-input-number v-model="createForm.price" :min="0" :precision="2" controls-position="right" />
          </el-form-item>
          <el-form-item label="原价">
            <el-input-number v-model="createForm.originalPrice" :min="0" :precision="2" controls-position="right" />
          </el-form-item>
          <el-form-item label="成色">
            <el-select v-model="createForm.condition" placeholder="选择成色">
              <el-option v-for="condition in conditions" :key="condition" :label="condition" :value="condition" />
            </el-select>
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="createForm.category" placeholder="选择分类">
              <el-option v-for="category in categoryOptions" :key="category" :label="category" :value="category" />
            </el-select>
          </el-form-item>
          <el-form-item label="校区">
            <el-select v-model="createForm.campus" placeholder="选择校区">
              <el-option v-for="campus in campuses" :key="campus" :label="campus" :value="campus" />
            </el-select>
          </el-form-item>
          <el-form-item label="宿舍楼 / 教学楼">
            <el-input v-model="createForm.dormitory" />
          </el-form-item>
          <el-form-item label="交易地点">
            <el-input v-model="createForm.tradePlace" />
          </el-form-item>
          <el-form-item label="交易模式">
            <el-checkbox-group v-model="createForm.tradeModes">
              <el-checkbox label="面交" />
              <el-checkbox label="线上担保" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="支持置换">
            <el-switch v-model="createForm.swapSupported" />
          </el-form-item>
        </div>
        <el-form-item label="商品描述">
          <el-input v-model="createForm.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="封面图片 URL">
          <el-input v-model="createForm.imageUrl" placeholder="可选；不填则使用默认图片" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createItem">保存商品</el-button>
      </template>
    </el-dialog>
  </div>
</template>
