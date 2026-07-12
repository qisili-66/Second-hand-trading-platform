import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'campusTradeAuth'

function readSession() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) || null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref(readSession())
  const isLoggedIn = computed(() => Boolean(user.value))
  const isAdmin = computed(() => user.value?.role === 'admin')

  function login(payload = {}) {
    const role = payload.role || 'user'
    const profile = role === 'admin' ? payload.admin || {} : payload.user || {}
    const account = payload.account || profile.studentNo || profile.username || ''

    user.value = {
      ...profile,
      role,
      account,
      nickname: profile.nickname || profile.realName || profile.username || account,
    }

    if (payload.accessToken) {
      localStorage.setItem('accessToken', payload.accessToken)
    }

    localStorage.setItem(STORAGE_KEY, JSON.stringify(user.value))
  }

  function logout() {
    user.value = null
    localStorage.removeItem(STORAGE_KEY)
    localStorage.removeItem('accessToken')
  }

  function updateProfile(profile = {}) {
    user.value = {
      ...(user.value || {}),
      ...profile,
      nickname: profile.nickname || user.value?.nickname || profile.realName || user.value?.realName || user.value?.account,
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(user.value))
  }

  return {
    user,
    isLoggedIn,
    isAdmin,
    login,
    logout,
    updateProfile,
  }
})
