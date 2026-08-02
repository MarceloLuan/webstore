import { computed, readonly, ref } from 'vue'
import {
  clearUser,
  getAuthToken,
  getUser,
  setAuthToken,
  setUser,
} from '@/services/auth'

const user = ref(getUser())
const token = ref(getAuthToken())

const isAuthenticated = computed(() => Boolean(user.value && token.value))
const isAdmin = computed(() => user.value?.role === 'ADMIN')

function setSession(authToken, authenticatedUser) {
  setAuthToken(authToken)
  setUser(authenticatedUser)
  token.value = authToken
  user.value = authenticatedUser
}

function updateUser(updatedUser) {
  setUser(updatedUser)
  user.value = updatedUser
}

function logout() {
  clearUser()
  token.value = null
  user.value = null
}

export function useAuthStore() {
  return {
    user: readonly(user),
    token: readonly(token),
    isAuthenticated,
    isAdmin,
    setSession,
    updateUser,
    logout,
  }
}
