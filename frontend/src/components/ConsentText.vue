<script setup lang="ts">
import { computed } from 'vue'

// The consent texts are small Markdown: "## heading", "- item" and paragraphs.
// Rendered as elements, never as HTML.
const props = defineProps<{ text: string }>()

type Block = { kind: 'h' | 'p'; text: string } | { kind: 'ul'; items: string[] }

const blocks = computed<Block[]>(() => {
  const out: Block[] = []
  for (const raw of props.text.split('\n')) {
    const line = raw.trim()
    if (!line) continue
    if (line.startsWith('## ')) out.push({ kind: 'h', text: line.slice(3) })
    else if (line.startsWith('- ')) {
      const last = out[out.length - 1]
      if (last && last.kind === 'ul') last.items.push(line.slice(2))
      else out.push({ kind: 'ul', items: [line.slice(2)] })
    } else out.push({ kind: 'p', text: line })
  }
  return out
})
</script>

<template>
  <template v-for="(b, i) in blocks" :key="i">
    <h2 v-if="b.kind === 'h'">{{ b.text }}</h2>
    <ul v-else-if="b.kind === 'ul'">
      <li v-for="item in b.items" :key="item">{{ item }}</li>
    </ul>
    <p v-else>{{ b.text }}</p>
  </template>
</template>
