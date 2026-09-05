<script setup lang="ts">
const props = withDefaults(defineProps<{ page: number; pageSize?: number; total: number; totalPages: number; loading?: boolean; pageSizes?: number[] }>(), {
  pageSize: 20,
  loading: false,
  pageSizes: () => [20, 50, 100],
})
const emit = defineEmits<{ 'update:page': [page: number]; 'update:pageSize': [pageSize: number] }>()
function move(page: number) { if (!props.loading && page >= 1 && page <= Math.max(props.totalPages, 1)) emit('update:page', page) }
function resize(event: Event) { emit('update:pageSize', Number((event.target as HTMLSelectElement).value)) }
</script>

<template>
  <nav class="table-pagination" aria-label="列表分页">
    <span>共 {{ total }} 条，第 {{ page }} / {{ Math.max(totalPages, 1) }} 页</span>
    <label v-if="pageSizes.length" class="pagination-size">每页
      <select :value="pageSize" :disabled="loading" aria-label="每页条数" @change="resize">
        <option v-for="size in pageSizes" :key="size" :value="size">{{ size }}</option>
      </select>
    </label>
    <button class="btn btn--secondary" type="button" :disabled="page <= 1 || loading" aria-label="上一页" @click="move(page - 1)">上一页</button>
    <button class="btn btn--secondary" type="button" :disabled="page >= totalPages || loading" aria-label="下一页" @click="move(page + 1)">下一页</button>
  </nav>
</template>
