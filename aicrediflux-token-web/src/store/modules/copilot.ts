import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useCopilotStore = defineStore('copilot', () => {
  const open = ref(false)

  function toggle(): void {
    open.value = !open.value
  }

  function setOpen(next: boolean): void {
    open.value = next
  }

  return {
    open,
    toggle,
    setOpen
  }
})
