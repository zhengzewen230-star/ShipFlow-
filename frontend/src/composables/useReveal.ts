import { onMounted, onUnmounted, ref, type Ref } from 'vue'

export function useReveal(target: Ref<HTMLElement | null>, options: IntersectionObserverInit = {}) {
  const revealed = ref(false)
  let observer: IntersectionObserver | undefined

  onMounted(() => {
    if (!target.value) return
    observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        revealed.value = true
        observer?.disconnect()
      }
    }, { threshold: 0.18, ...options })
    observer.observe(target.value)
  })

  onUnmounted(() => observer?.disconnect())
  return { revealed }
}
