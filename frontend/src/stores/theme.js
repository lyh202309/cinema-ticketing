import { defineStore } from 'pinia'
import { applyTheme } from '../theme'

export const useThemeStore = defineStore('theme', {
  state: () => ({
    key: localStorage.getItem('theme') || 'red',
  }),
  actions: {
    init() {
      applyTheme(this.key)
    },
    setKey(key) {
      this.key = key
      localStorage.setItem('theme', key)
      applyTheme(key)
    },
  },
})
