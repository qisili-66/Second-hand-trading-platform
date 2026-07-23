<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductGridCard from '../../components/product/ProductGridCard.vue'
import ReviewList from '../../components/ReviewList.vue'
import { userApi } from '../../services/api'
import { normalizeItemPage } from '../../services/normalizers'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const user = ref({})
const products = ref([])

const userId = computed(() => route.params.userId)
const displayName = computed(() => user.value.nickname || `用户${userId.value}`)
const avatarText = computed(() => displayName.value.slice(0, 1) || '用')
const creditScore = computed(() => Math.min(Math.max(Number(user.value.creditScore) || 0, 0), 100))
const profileLine = computed(() => [user.value.campus, user.value.department].filter(Boolean).join(' · ') || '校园二手卖家')

watch(
  userId,
  () => fetchProfile(),
  { immediate: true },
)

async function fetchProfile() {
  if (!userId.value) return
  loading.value = true
  user.value = {}
  products.value = []
  try {
    const [profileResponse, itemsResponse] = await Promise.all([
      userApi.publicProfile(userId.value),
      userApi.publicItems(userId.value, { page: 1, pageSize: 12 }),
    ])
    user.value = profileResponse.data || profileResponse || {}
    products.value = normalizeItemPage(itemsResponse).list
  } catch (error) {
    user.value = {}
    products.value = []
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="page-wrap public-profile-page" v-loading="loading">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>个人主页</el-breadcrumb-item>
      <el-breadcrumb-item>{{ displayName }}</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="profile-hero public-profile-hero" shadow="never">
      <div class="profile-user">
        <el-avatar :size="82" :src="user.avatarUrl">{{ avatarText }}</el-avatar>
        <div>
          <h1>{{ displayName }}</h1>
          <p>{{ profileLine }}</p>
          <el-rate :model-value="5" disabled show-score :score-template="`信用分 ${creditScore}`" />
        </div>
      </div>
      <div class="profile-stats">
        <div><strong>{{ products.length }}</strong><span>在售商品</span></div>
        <div><strong>{{ creditScore }}</strong><span>信用分</span></div>
      </div>
    </el-card>

    <section class="public-profile-section">
      <div class="section-head">
        <div>
          <p class="section-kicker">TA 的闲置</p>
          <h2>正在出售</h2>
        </div>
      </div>
      <div v-if="products.length > 0" class="product-grid three">
        <ProductGridCard v-for="product in products" :key="product.id" :product="product" />
      </div>
      <el-empty v-else description="TA 暂无在售商品">
        <el-button type="primary" @click="router.push('/items')">去逛商品</el-button>
      </el-empty>
    </section>

    <section class="public-profile-section">
      <el-card shadow="never">
        <template #header>卖家评价</template>
        <ReviewList :user-id="userId" />
      </el-card>
    </section>
  </main>
</template>
