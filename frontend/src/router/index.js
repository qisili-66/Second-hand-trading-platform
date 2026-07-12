import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('../views/front/HomeView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/front/LoginView.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/front/RegisterView.vue'),
    },
    {
      path: '/items',
      name: 'item-list',
      component: () => import('../views/front/SearchResultsView.vue'),
    },
    {
      path: '/items/publish',
      name: 'item-publish',
      component: () => import('../views/front/PublishItemView.vue'),
    },
    {
      path: '/items/:itemId',
      name: 'item-detail',
      component: () => import('../views/front/ItemDetailView.vue'),
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('../views/front/ProfileCenterView.vue'),
    },
    {
      path: '/chats',
      name: 'chats',
      component: () => import('../views/front/ChatView.vue'),
    },
    {
      path: '/wanted',
      name: 'wanted',
      component: () => import('../views/front/BazaarView.vue'),
      meta: { tab: 'wanted' },
    },
    {
      path: '/swap',
      name: 'swap',
      component: () => import('../views/front/BazaarView.vue'),
      meta: { tab: 'swap' },
    },
    {
      path: '/season',
      name: 'season',
      component: () => import('../views/front/BazaarView.vue'),
      meta: { tab: 'season' },
    },
    {
      path: '/orders',
      name: 'orders',
      component: () => import('../views/front/OrdersView.vue'),
    },
    {
      path: '/search',
      name: 'search',
      component: () => import('../views/front/SearchResultsView.vue'),
    },
    {
      path: '/help',
      name: 'help',
      component: () => import('../views/front/HelpView.vue'),
    },
    {
      path: '/admin',
      component: () => import('../layouts/AdminLayout.vue'),
      children: [
        {
          path: '',
          name: 'admin-dashboard',
          component: () => import('../views/admin/AdminDashboardView.vue'),
          meta: { title: '首页数据大盘' },
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('../views/admin/AdminUsersView.vue'),
          meta: { title: '用户管理' },
        },
        {
          path: 'items',
          name: 'admin-items',
          component: () => import('../views/admin/AdminItemsView.vue'),
          meta: { title: '商品管理' },
        },
        {
          path: 'categories',
          name: 'admin-categories',
          component: () => import('../views/admin/AdminCategoriesView.vue'),
          meta: { title: '分类管理' },
        },
        {
          path: 'orders',
          name: 'admin-orders',
          component: () => import('../views/admin/AdminOrdersView.vue'),
          meta: { title: '订单&纠纷管理' },
        },
        {
          path: 'reports',
          name: 'admin-reports',
          component: () => import('../views/admin/AdminReportsView.vue'),
          meta: { title: '举报审核管理' },
        },
        {
          path: 'settings',
          name: 'admin-settings',
          component: () => import('../views/admin/AdminSettingsView.vue'),
          meta: { title: '系统配置' },
        },
        {
          path: 'notices',
          name: 'admin-notices',
          component: () => import('../views/admin/AdminNoticesView.vue'),
          meta: { title: '公告管理' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})

export default router
