<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { AlertTriangle } from '@lucide/vue'
import { useConfirmAction } from '@/composables/useConfirmAction'

const { state, accept, cancel } = useConfirmAction()
const cancelButton = ref<HTMLButtonElement>()

watch(() => state.open, open => {
  if (open) void nextTick(() => cancelButton.value?.focus())
})

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') cancel()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="state.open" class="confirm-overlay" @keydown="onKeydown">
      <section class="confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title" aria-describedby="confirm-description">
        <AlertTriangle :size="24" aria-hidden="true" />
        <div>
          <h2 id="confirm-title">{{ state.title }}</h2>
          <p id="confirm-description">{{ state.description }}</p>
        </div>
        <div class="confirm-dialog__actions">
          <button ref="cancelButton" class="btn btn--secondary" type="button" @click="cancel">{{ state.cancelLabel }}</button>
          <button class="btn" :class="state.danger ? 'btn--danger' : 'btn--primary'" type="button" @click="accept">{{ state.confirmLabel }}</button>
        </div>
      </section>
    </div>
  </Teleport>
</template>
