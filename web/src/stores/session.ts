import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Actor } from '../types/api'
import { api } from '../api'
import { clearSession, readToken, tokenKey } from '../api/client'
export const useSession = defineStore('session', () => {
  const actor = ref<Actor | null>(null)
  async function restore() {
    if (!readToken()) {
      actor.value = null
      return
    }
    actor.value = (await api.me()).data
  }
  async function login(username: string, password: string) {
    const result = (await api.login(username, password)).data
    sessionStorage.setItem(tokenKey, result.token)
    actor.value = result.user
  }
  async function logout() {
    await api.logout()
    actor.value = null
    clearSession()
  }
  window.addEventListener('session-expired', () => {
    actor.value = null
  })
  return { actor, restore, login, logout }
})
