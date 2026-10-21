<script setup>
import { computed, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import markdownItAnchor from 'markdown-it-anchor'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import kotlin from 'highlight.js/lib/languages/kotlin'
import markdown from 'highlight.js/lib/languages/markdown'
import plaintext from 'highlight.js/lib/languages/plaintext'
import powershell from 'highlight.js/lib/languages/powershell'
import toml from 'highlight.js/lib/languages/ini'
import xml from 'highlight.js/lib/languages/xml'
import 'highlight.js/styles/github.css'

;[['bash', bash], ['kotlin', kotlin], ['markdown', markdown], ['text', plaintext], ['powershell', powershell], ['toml', toml], ['xml', xml]].forEach(([name, language]) => hljs.registerLanguage(name, language))

const files = import.meta.glob('../*.md', { eager: true, query: '?raw', import: 'default' })
// 只上站读者向章节；维护者文档（README/AGENTS/审计报告）不上站
const HIDDEN_FILES = new Set(['README.md', 'AGENTS.md', 'docs-expansion-audit.md', 'docs-expansion-final-report.md', 'docs-expansion-progress.md'])
const HOME_FILE = '00_完整学习规划.md'
const docs = Object.entries(files)
  .map(([path, content]) => {
    const file = decodeURIComponent(path.split('/').pop())
    const title = content.match(/^#\s+(.+)$/m)?.[1]?.replace(/`/g, '') || file.replace(/\.md$/, '')
    const number = file.match(/^(\d+)([A-Z]?)/)
    return { id: file, title, content, number: number ? Number(number[1]) : 999, file }
  })
  .filter((doc) => !HIDDEN_FILES.has(doc.file))
  .sort((a, b) => {
    if (a.file === HOME_FILE) return -1
    if (b.file === HOME_FILE) return 1
    return a.number - b.number || a.file.localeCompare(b.file, 'zh-CN')
  })

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  highlight(code, language) {
    if (language && hljs.getLanguage(language)) return `<pre class="hljs"><code>${hljs.highlight(code, { language }).value}</code></pre>`
    return `<pre class="hljs"><code>${md.utils.escapeHtml(code)}</code></pre>`
  },
}).use(markdownItAnchor, { permalink: false })
md.core.ruler.push('compose_callouts', (state) => {
  const labels = { NOTE: '提示', TIP: '建议', IMPORTANT: '重点', WARNING: '注意', CAUTION: '警告' }
  for (let i = 0; i < state.tokens.length; i++) {
    const token = state.tokens[i]
    if (token.type !== 'inline' || !token.content.startsWith('[!')) continue
    const match = token.content.match(/^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*/)
    if (!match) continue
    token.content = token.content.slice(match[0].length)
    if (token.children?.[0]?.type === 'text') token.children[0].content = token.children[0].content.replace(match[0], '')
    const open = state.tokens.slice(0, i).reverse().find((item) => item.type === 'blockquote_open')
    if (open) {
      open.attrSet('class', `callout callout-${match[1].toLowerCase()}`)
      open.attrSet('data-label', labels[match[1]])
    }
  }
})
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
const activeId = ref(decodeURIComponent(location.hash.slice(1)))
const menuOpen = ref(false)
const active = computed(() => docs.find((doc) => doc.id === activeId.value) || docs[0])
const homeDoc = docs[0]
const currentIndex = computed(() => docs.findIndex((doc) => doc.id === active.value?.id))
const previousDoc = computed(() => currentIndex.value > 0 ? docs[currentIndex.value - 1] : null)
const nextDoc = computed(() => currentIndex.value >= 0 && currentIndex.value < docs.length - 1 ? docs[currentIndex.value + 1] : null)
const filteredDocs = computed(() => docs.filter((doc) => !query.value || `${doc.title} ${doc.file}`.toLowerCase().includes(query.value.toLowerCase())))
const groups = computed(() => {
  const sections = [
    { title: '开始学习', test: (n) => n === 0 },
    { title: '基础篇', test: (n) => n >= 1 && n <= 45 },
    { title: '进阶篇', test: (n) => n >= 46 && n <= 80 },
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
// hash 不是响应式的，必须监听 hashchange：正文里的章节链接、浏览器前进后退都走这里
function syncFromHash() {
  const id = decodeURIComponent(location.hash.slice(1))
  const target = !id ? docs[0].id : docs.some((doc) => doc.id === id) ? id : null
  if (!target || target === activeId.value) return
  activeId.value = target
  window.scrollTo(0, 0)
}
window.addEventListener('hashchange', syncFromHash)
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
    <a class="brand" href="#" @click.prevent="select(homeDoc)"><span class="brand-mark">H</span><span>Hax Compose101</span></a>
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
        <nav v-if="previousDoc || nextDoc" class="article-pagination" aria-label="章节导航">
          <button v-if="previousDoc" class="pagination-card pagination-previous" @click="select(previousDoc)">
            <span class="pagination-label">← 上一章节</span>
            <span class="pagination-title">{{ previousDoc.title }}</span>
          </button>
          <span v-else />
          <button v-if="nextDoc" class="pagination-card pagination-next" @click="select(nextDoc)">
            <span class="pagination-label">下一章节 →</span>
            <span class="pagination-title">{{ nextDoc.title }}</span>
          </button>
        </nav>
        <footer class="article-footer"><span>本文档面向 Android Jetpack Compose</span><a href="https://github.com/xiehanff/compose-learning-docs">GitHub ↗</a></footer>
      </div>
    </main>
  </div>
</template>
