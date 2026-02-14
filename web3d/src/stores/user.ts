import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/api/types'

export const useUserStore = defineStore('user', () => {
    const token = ref<string>(localStorage.getItem('token') || '')
    const userInfo = ref<UserInfo | null>(
        (() => {
            try {
                const stored = localStorage.getItem('user')
                return stored ? JSON.parse(stored) : null
            } catch {
                return null
            }
        })()
    )

    const isLoggedIn = computed(() => !!token.value)
    const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')

    function setAuth(newToken: string, user: UserInfo) {
        token.value = newToken
        userInfo.value = user
        localStorage.setItem('token', newToken)
        localStorage.setItem('user', JSON.stringify(user))
    }

    function clearAuth() {
        token.value = ''
        userInfo.value = null
        localStorage.removeItem('token')
        localStorage.removeItem('user')
    }

    return { token, userInfo, isLoggedIn, nickname, setAuth, clearAuth }
})
