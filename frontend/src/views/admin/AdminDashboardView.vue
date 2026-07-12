<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AdminChart from '../../components/admin/AdminChart.vue'
import { adminApi } from '../../services/api'

const router = useRouter()
const loading = ref(false)
const dashboard = ref({
  totalUsers: 0,
  todayNewUsers: 0,
  totalItems: 0,
  onSaleItems: 0,
  todayAmount: 0,
  totalAmount: 0,
  activeUsers: 0,
  pendingVerifiedUsers: 0,
  pendingReports: 0,
  pendingDisputes: 0,
  pendingOrders: 0,
  amountTrend: [],
  categoryDistribution: [],
  campusDistribution: [],
})

const numberText = (value) => String(Number(value) || 0)
const moneyText = (value) => `￥${(Number(value) || 0).toFixed(2)}`

const adminStats = computed(() => [
  { label: '总用户数', value: numberText(dashboard.value.totalUsers), trend: 'users 表' },
  { label: '今日新增用户', value: numberText(dashboard.value.todayNewUsers), trend: '今日注册' },
  { label: '在售商品总量', value: numberText(dashboard.value.onSaleItems), trend: `全部商品 ${numberText(dashboard.value.totalItems)}` },
  { label: '今日交易额', value: moneyText(dashboard.value.todayAmount), trend: `累计 ${moneyText(dashboard.value.totalAmount)}` },
  { label: '活跃用户数', value: numberText(dashboard.value.activeUsers), trend: '今日登录/交易/聊天' },
])

const lineOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 36, right: 20, top: 40, bottom: 32 },
  xAxis: {
    type: 'category',
    data: dashboard.value.amountTrend.map((item) => item.date),
  },
  yAxis: { type: 'value' },
  series: [
    {
      name: '交易额',
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.12 },
      itemStyle: { color: '#ff7800' },
      data: dashboard.value.amountTrend.map((item) => Number(item.amount) || 0),
    },
  ],
}))

const pieOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [
    {
      name: '商品占比',
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '46%'],
      data: dashboard.value.categoryDistribution.map((item) => ({
        name: item.category,
        value: Number(item.count) || 0,
      })),
    },
  ],
}))

const barOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 38, right: 20, top: 40, bottom: 32 },
  xAxis: { type: 'category', data: dashboard.value.campusDistribution.map((item) => item.campus) },
  yAxis: { type: 'value' },
  series: [
    {
      name: '商品数',
      type: 'bar',
      itemStyle: { color: '#ff7800', borderRadius: [6, 6, 0, 0] },
      data: dashboard.value.campusDistribution.map((item) => Number(item.count) || 0),
    },
  ],
}))

async function fetchDashboard() {
  loading.value = true
  try {
    const response = await adminApi.dashboard()
    dashboard.value = {
      ...dashboard.value,
      ...(response.data || {}),
      amountTrend: response.data?.amountTrend || [],
      categoryDistribution: response.data?.categoryDistribution || [],
      campusDistribution: response.data?.campusDistribution || [],
    }
  } catch (error) {
    ElMessage.error(error.message || '数据大盘加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(fetchDashboard)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <section class="admin-stat-grid">
      <el-card v-for="stat in adminStats" :key="stat.label" shadow="never" class="admin-stat-card">
        <span>{{ stat.label }}</span>
        <strong>{{ stat.value }}</strong>
        <small>{{ stat.trend }}</small>
      </el-card>
    </section>

    <section class="admin-chart-grid">
      <el-card shadow="never" class="chart-card wide">
        <template #header>近 7 日交易额走势</template>
        <AdminChart :option="lineOption" />
      </el-card>
      <el-card shadow="never" class="chart-card">
        <template #header>各分类商品占比</template>
        <AdminChart :option="pieOption" />
      </el-card>
      <el-card shadow="never" class="chart-card wide">
        <template #header>各校区商品分布</template>
        <AdminChart :option="barOption" />
      </el-card>
    </section>

    <section class="admin-quick-grid">
      <el-card shadow="hover" @click="router.push('/admin/users')">
        <strong>待审核实名用户</strong>
        <span>{{ dashboard.pendingVerifiedUsers }} 人</span>
      </el-card>
      <el-card shadow="hover" @click="router.push('/admin/reports')">
        <strong>待处理举报</strong>
        <span>{{ dashboard.pendingReports }} 条</span>
      </el-card>
      <el-card shadow="hover" @click="router.push('/admin/orders')">
        <strong>待处理订单 / 纠纷</strong>
        <span>{{ dashboard.pendingOrders }} 单 / {{ dashboard.pendingDisputes }} 单</span>
      </el-card>
    </section>
  </div>
</template>
