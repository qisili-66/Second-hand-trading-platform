<script setup>
defineProps({
  notifications: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['view-all'])
</script>

<template>
  <div class="notice-panel">
    <h3>消息通知</h3>
    <ul v-if="notifications.length > 0">
      <li v-for="notice in notifications.slice(0, 5)" :key="notice.id || `${notice.type}-${notice.createdAt}`">
        <strong>{{ notice.title }}</strong>
        <p>{{ notice.content }}</p>
        <small>{{ notice.createdAt }}</small>
      </li>
    </ul>
    <el-empty v-else description="暂无消息" :image-size="72" />
    <el-button text type="primary" @click="emit('view-all')">查看全部通知</el-button>
  </div>
</template>
