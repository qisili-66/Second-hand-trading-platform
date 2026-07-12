<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import ProductGridCard from '../../components/product/ProductGridCard.vue'
import { itemApi } from '../../services/api'
import { normalizeItemPage } from '../../services/normalizers'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const activeTab = ref('latest')
const products = ref([])
const loading = ref(false)

const recommendedProducts = computed(() => {
  if (activeTab.value === 'hot') {
    return [...products.value].sort((a, b) => b.hot - a.hot).slice(0, 4)
  }

  if (activeTab.value === 'near') {
    return products.value.filter((product) => product.campus === '校本部').slice(0, 4)
  }

  return products.value.slice(0, 4)
})

async function fetchRecommendedProducts() {
  loading.value = true
  try {
    const response = await itemApi.list({ page: 1, pageSize: 8, sort: 'latest' })
    products.value = normalizeItemPage(response).list
  } catch (error) {
    products.value = []
    console.error(error)
  } finally {
    loading.value = false
  }
}

function goPublish() {
  if (authStore.isLoggedIn) {
    router.push('/items/publish')
    return
  }

  ElMessageBox.confirm('还没登录，登录或注册后才能免费发布商品。', '需要登录', {
    confirmButtonText: '前往登录',
    cancelButtonText: '前往注册',
    distinguishCancelAndClose: true,
    type: 'warning',
  })
    .then(() => router.push('/login'))
    .catch((action) => {
      if (action === 'cancel') router.push('/register')
    })
}

function goItemList() {
  router.push('/items')
}

onMounted(fetchRecommendedProducts)
</script>

<template>
  <main class="home-page page-wrap">
    <section class="notice-strip">
      <span>已根据你的校区推荐附近二手商品，优先展示同校区可面交闲置。</span>
    </section>

    <section class="hero-panel">
      <div>
        <p class="section-kicker">同校区面交 · 轻量可信</p>
        <h1>校园闲置，先看身边同学在出什么</h1>
        <p>教材、数码、宿舍好物、毕业季清仓，一站式按校区筛选。</p>
      </div>
      <div class="hero-actions">
        <el-button size="large" @click="goItemList">查看全部商品</el-button>
        <el-button type="primary" size="large" @click="goPublish">免费发布商品</el-button>
        <el-button size="large" @click="router.push('/wanted')">发布求购</el-button>
      </div>
    </section>

    <section class="content-section">
      <div class="section-head">
        <div>
          <p class="section-kicker">首页推荐</p>
          <h2>猜你想看</h2>
        </div>
        <div class="section-actions">
          <el-button type="primary" plain @click="goItemList">进入商品列表</el-button>
          <el-tabs v-model="activeTab" class="compact-tabs">
            <el-tab-pane label="最新发布" name="latest" />
            <el-tab-pane label="热门商品" name="hot" />
            <el-tab-pane label="附近商品" name="near" />
          </el-tabs>
        </div>
      </div>

      <div v-if="recommendedProducts.length > 0" v-loading="loading" class="product-grid">
        <ProductGridCard v-for="product in recommendedProducts" :key="product.id" :product="product" />
      </div>
      <el-empty v-else-if="!loading" description="暂无商品，注册登录后可以发布第一件闲置" />
    </section>
  </main>
</template>
