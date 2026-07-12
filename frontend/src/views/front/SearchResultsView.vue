<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductListItem from '../../components/product/ProductListItem.vue'
import { campuses, categories, conditions } from '../../data/mock'
import { itemApi } from '../../services/api'
import { normalizeItemPage } from '../../services/normalizers'

const route = useRoute()
const router = useRouter()

function asArray(value) {
  if (!value) return []
  return Array.isArray(value) ? value : [value]
}

function readFiltersFromQuery() {
  return {
    minPrice: route.query.minPrice || '',
    maxPrice: route.query.maxPrice || '',
    campus: route.query.campus || '',
    conditions: asArray(route.query.conditions),
    categories: asArray(route.query.categories),
    sort: route.query.sort || 'latest',
  }
}

function assignFilters(target, source) {
  target.minPrice = source.minPrice
  target.maxPrice = source.maxPrice
  target.campus = source.campus
  target.conditions = [...source.conditions]
  target.categories = [...source.categories]
  target.sort = source.sort
}

const filterForm = reactive(readFiltersFromQuery())
const appliedFilters = reactive(readFiltersFromQuery())
const pageSize = 10
const currentPage = ref(Math.max(1, Number(route.query.page) || 1))
const productList = ref([])
const productsTotal = ref(0)
const isLoading = ref(false)

const keyword = computed(() => String(route.query.keyword || '').trim())
const pageTitle = computed(() => (keyword.value ? `"${keyword.value}" 相关商品` : '全部商品'))
const filteredProducts = computed(() => productList.value)
const pagedProducts = computed(() => productList.value)

watch(
  () => route.query,
  () => {
    const filters = readFiltersFromQuery()
    assignFilters(filterForm, filters)
    assignFilters(appliedFilters, filters)
    currentPage.value = Math.max(1, Number(route.query.page) || 1)
    fetchProducts()
  },
  { immediate: true },
)

function buildQuery(filters) {
  const query = {}

  if (keyword.value) query.keyword = keyword.value
  if (filters.minPrice !== '') query.minPrice = filters.minPrice
  if (filters.maxPrice !== '') query.maxPrice = filters.maxPrice
  if (filters.campus) query.campus = filters.campus
  if (filters.conditions.length > 0) query.conditions = filters.conditions
  if (filters.categories.length > 0) query.categories = filters.categories
  if (filters.sort !== 'latest') query.sort = filters.sort
  if (currentPage.value > 1) query.page = currentPage.value

  return query
}

function buildApiParams() {
  const params = {
    page: currentPage.value,
    pageSize,
    sort: appliedFilters.sort,
  }

  if (keyword.value) params.keyword = keyword.value
  if (appliedFilters.minPrice !== '') params.minPrice = appliedFilters.minPrice
  if (appliedFilters.maxPrice !== '') params.maxPrice = appliedFilters.maxPrice
  if (appliedFilters.campus) params.campus = appliedFilters.campus
  if (appliedFilters.categories.length > 0) params.categories = appliedFilters.categories.join(',')
  if (appliedFilters.conditions.length > 0) params.conditions = appliedFilters.conditions.join(',')

  return params
}

async function fetchProducts() {
  isLoading.value = true
  try {
    const response = await itemApi.list(buildApiParams())
    const page = normalizeItemPage(response)
    productList.value = page.list
    productsTotal.value = page.total
  } catch (error) {
    productList.value = []
    productsTotal.value = 0
    console.error(error)
  } finally {
    isLoading.value = false
  }
}

function applyFilters() {
  assignFilters(appliedFilters, filterForm)
  currentPage.value = 1
  router.replace({ path: '/items', query: buildQuery(appliedFilters) })
}

function resetFilters() {
  assignFilters(filterForm, {
    minPrice: '',
    maxPrice: '',
    campus: '',
    conditions: [],
    categories: [],
    sort: 'latest',
  })
  applyFilters()
}

function changePage(page) {
  currentPage.value = page
  router.replace({ path: '/items', query: buildQuery(appliedFilters) })
}
</script>

<template>
  <main class="page-wrap search-page item-list-page">
    <div class="page-title">
      <p class="section-kicker">商品列表</p>
      <h1>{{ pageTitle }}</h1>
    </div>

    <div class="item-list-layout">
      <aside class="search-filter-card item-filter-panel">
        <div class="filter-card-title">
          <h2>筛选商品</h2>
          <span>点击确认后刷新结果</span>
        </div>

        <el-form label-position="top">
          <el-form-item label="价格区间">
            <div class="price-range">
              <el-input v-model="filterForm.minPrice" placeholder="最低价" />
              <span>-</span>
              <el-input v-model="filterForm.maxPrice" placeholder="最高价" />
            </div>
          </el-form-item>

          <el-form-item label="成色">
            <el-checkbox-group v-model="filterForm.conditions">
              <el-checkbox v-for="condition in conditions" :key="condition" :label="condition" />
            </el-checkbox-group>
          </el-form-item>

          <el-form-item label="校区">
            <el-select v-model="filterForm.campus" clearable placeholder="全部校区">
              <el-option v-for="campus in campuses" :key="campus" :label="campus" :value="campus" />
            </el-select>
          </el-form-item>

          <el-form-item label="分类">
            <el-checkbox-group v-model="filterForm.categories">
              <el-checkbox v-for="category in categories" :key="category" :label="category" />
            </el-checkbox-group>
          </el-form-item>

          <el-form-item label="排序">
            <el-select v-model="filterForm.sort">
              <el-option label="发布时间" value="latest" />
              <el-option label="价格从低到高" value="price_asc" />
              <el-option label="价格从高到低" value="price_desc" />
              <el-option label="距离优先" value="distance" />
            </el-select>
          </el-form-item>
        </el-form>

        <div class="filter-actions">
          <el-button type="primary" @click="applyFilters">确认筛选</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </aside>

      <section class="content-section item-list-results">
        <div class="section-head">
          <div>
            <p class="section-kicker">筛选后商品</p>
            <h2>共 {{ productsTotal }} 件结果</h2>
          </div>
        </div>

        <div v-if="filteredProducts.length > 0" v-loading="isLoading" class="product-list">
          <ProductListItem v-for="product in pagedProducts" :key="product.id" :product="product" />
        </div>
        <el-empty v-else-if="!isLoading" description="暂无符合条件的商品" />

        <div v-if="productsTotal > pageSize" class="list-pagination">
          <el-pagination
            v-model:current-page="currentPage"
            background
            layout="prev, pager, next, jumper, total"
            :page-size="pageSize"
            :total="productsTotal"
            @current-change="changePage"
          />
        </div>
      </section>
    </div>
  </main>
</template>
