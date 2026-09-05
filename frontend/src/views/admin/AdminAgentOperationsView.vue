<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../services/api'

const loading = ref(false)
const operations = ref({ failureBreakdown: [] })
const stats = computed(() => [
  { label: '近 30 天运行', value: operations.value.runsLast30Days || 0 },
  { label: '成功运行', value: operations.value.successfulRunsLast30Days || 0 },
  { label: '失败运行', value: operations.value.failedRunsLast30Days || 0 },
  { label: '平均工具耗时', value: `${operations.value.averageToolDurationMs || 0} ms` },
  { label: '推荐记录', value: operations.value.recommendationsLast30Days || 0 },
  { label: '推荐后订单', value: operations.value.recommendedItemOrdersLast30Days || 0 },
])

async function load() {
  loading.value = true
  try {
    const response = await adminApi.agentOperations()
    operations.value = response.data || response || {}
  } catch (error) {
    ElMessage.error(error.message || 'Agent 运营指标加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page agent-operations-page" v-loading="loading">
    <el-alert type="info" :closable="false" title="指标仅统计已审计的 Agent Run、工具步骤、推荐快照和后续订单关联；不读取聊天正文，不代表因果归因。" />
    <section class="admin-stat-grid agent-operations-grid">
      <el-card v-for="stat in stats" :key="stat.label" class="admin-stat-card" shadow="never">
        <span>{{ stat.label }}</span><strong>{{ stat.value }}</strong><small>近 30 天</small>
      </el-card>
    </section>
    <el-card shadow="never" class="agent-failure-card">
      <template #header>失败类型分布</template>
      <el-table :data="operations.failureBreakdown || []" empty-text="近 30 天没有失败步骤">
        <el-table-column prop="errorCode" label="失败类型" />
        <el-table-column prop="count" label="次数" width="120" />
      </el-table>
      <p>{{ operations.note }}</p>
    </el-card>
  </div>
</template>
