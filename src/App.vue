<script setup>
import { computed, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'

const files = import.meta.glob('../*.md', { eager: true, query: '?raw', import: 'default' })
const docs = Object.entries(files)
  .map(([path, content]) => {
    const file = decodeURIComponent(path.split('/').pop())
    const title = content.match(/^#\s+(.+)$/m)?.[1]?.replace(/`/g, '') || file.replace(/\.md$/, '')
    const number = file.match(/^(\d+)([A-Z]?)/)
    return { id: file, title, content, number: number ? Number(number[1]) : 999, file }
  })
  .sort((a, b) => a.number - b.number || a.file.localeCompare(b.file, 'zh-CN'))

const md = new MarkdownIt({ html: true, linkify: true, typographer: true })
const defaultLinkOpen = md.renderer.rules.link_open || ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const hrefIndex = token.attrIndex('href')
  if (hrefIndex >= 0 && token.attrs[hrefIndex][1].endsWith('.md')) {
    const filename = decodeURIComponent(token.attrs[hrefIndex][1].split('/').pop())
    if (docs.some((doc) => doc.id === filename)) token.attrs[hrefIndex][1] = `#${encodeURIComponent(filename)}`
  }
  return defaultLinkOpen(tokens, idx, options, env, self)
}
const query = ref('')
const activeId = ref(location.hash.slice(1) || 'README.md')
const menuOpen = ref(false)
const active = computed(() => docs.find((doc) => doc.id === activeId.value) || docs.find((doc) => doc.id === 'README.md') || docs[0])
const filteredDocs = computed(() => docs.filter((doc) => !query.value || `${doc.title} ${doc.file}`.toLowerCase().includes(query.value.toLowerCase())))
const groups = computed(() => {
  const sections = [
    { title: '开始学习', test: (n) => n === 0 },
    { title: '基础篇', test: (n) => n >= 1 && n <= 45 },
    { title: '进阶篇', test: (n) => n >= 46 && n <= 80 },
    { title: '其他文档', test: (n) => n > 80 },
  ]
  return sections.map((section) => ({ ...section, docs: filteredDocs.value.filter((doc) => section.test(doc.number)) })).filter((section) => section.docs.length)
})
const rendered = computed(() => md.render(active.value?.content || ''))
function select(doc) {
  activeId.value = doc.id
  location.hash = encodeURIComponent(doc.id)
  menuOpen.value = false
  window.scrollTo(0, 0)
}
watch(() => location.hash, () => { activeId.value = decodeURIComponent(location.hash.slice(1)) })
window.addEventListener('keydown', (event) => {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    document.querySelector('.search input')?.focus()
  }
})
</script>

<template>
  <header class="topbar">
    <button class="menu-toggle" aria-label="切换目录" @click="menuOpen = !menuOpen">☰</button>
    <a class="brand" href="#README.md" @click.prevent="select(docs.find(d => d.id === 'README.md') || docs[0])"><span class="brand-mark">C</span><span>Compose 学习文档</span></a>
    <div class="top-actions"><span class="edition">中文教程 · Android Jetpack Compose</span><a href="https://github.com/xiehanff/compose-learning-docs" target="_blank" rel="noreferrer" aria-label="GitHub">GitHub ↗</a></div>
  </header>
  <div class="shell">
    <aside class="sidebar" :class="{ 'sidebar-open': menuOpen }">
      <div class="search"><span>⌕</span><input v-model="query" placeholder="搜索文档…" aria-label="搜索文档" /><kbd>⌘ K</kbd></div>
      <nav aria-label="文档目录">
        <section v-for="group in groups" :key="group.title" class="nav-group">
          <h2>{{ group.title }}</h2>
          <button v-for="doc in group.docs" :key="doc.id" class="nav-item" :class="{ active: active.id === doc.id }" @click="select(doc)">{{ doc.title }}</button>
        </section>
      </nav>
      <div class="sidebar-foot">学习文档 · 持续更新<br /><a href="https://github.com/xiehanff/compose-learning-docs">在 GitHub 上查看 ↗</a></div>
    </aside>
    <main class="main">
      <div class="article-wrap">
        <div class="article-meta"><span>JETPACK COMPOSE</span><a :href="`https://github.com/xiehanff/compose-learning-docs/blob/main/${encodeURIComponent(active.file)}`" target="_blank" rel="noreferrer">编辑此页 ↗</a></div>
        <article class="markdown-body" v-html="rendered" />
        <footer class="article-footer"><span>本文档面向 Android Jetpack Compose</span><a href="https://github.com/xiehanff/compose-learning-docs">GitHub ↗</a></footer>
      </div>
    </main>
  </div>
</template>
