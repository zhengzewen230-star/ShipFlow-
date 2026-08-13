<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Menu, X } from '@lucide/vue'
import BrandMark from './BrandMark.vue'

const open = ref(false)
const route = useRoute()
const router = useRouter()
const isHome = computed(() => route.path === '/')
const sectionLinks = [
  { label: '服务能力', hash: '#services' },
  { label: '物流渠道', hash: '#channels' },
  { label: '关于我们', hash: '#about' },
]

async function goToSection(hash: string) {
  open.value = false
  await router.push({ path: '/', hash })
}

async function goHome() {
  open.value = false
  await router.push({ path: '/', hash: '' })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function closeMenu() { open.value = false }
</script>

<template>
  <header class="navbar" :class="{ 'navbar--home': isHome }">
    <div class="container navbar__inner">
      <BrandMark @click.prevent="goHome" />
      <button class="navbar__toggle" type="button" :aria-expanded="open" aria-label="切换导航" @click="open = !open"><X v-if="open" :size="22" /><Menu v-else :size="22" /></button>
      <nav class="navbar__links" :class="{ 'is-open': open }" aria-label="主导航">
        <button v-for="link in sectionLinks" :key="link.hash" class="navbar__section-link" type="button" @click="goToSection(link.hash)">{{ link.label }}</button>
        <RouterLink to="/quote" @click="closeMenu">在线报价</RouterLink>
        <RouterLink class="navbar__quote" to="/apply" @click="closeMenu">获取报价</RouterLink>
        <RouterLink class="btn btn--nav" to="/login" @click="closeMenu">登录控制台</RouterLink>
      </nav>
    </div>
  </header>
</template>
