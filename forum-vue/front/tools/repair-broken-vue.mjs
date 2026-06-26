import fs from 'node:fs'
import path from 'node:path'
import { execSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const SRC = path.resolve(__dirname, '../src')
const ROOT = path.resolve(__dirname, '..')

function walk(dir, acc = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name)
    if (fs.statSync(full).isDirectory()) {
      if (name === 'node_modules') continue
      walk(full, acc)
    } else if (name.endsWith('.vue')) acc.push(full)
  }
  return acc
}

const broken = []
for (const vue of walk(SRC)) {
  const content = fs.readFileSync(vue, 'utf8')
  const scriptIdx = content.search(/<script setup src=/)
  if (scriptIdx < 0) continue
  const lastTemplateClose = content.lastIndexOf('</template>')
  if (lastTemplateClose < 0 || lastTemplateClose > scriptIdx) continue
  broken.push(path.relative(ROOT, vue).replace(/\\/g, '/'))
}

console.log('broken', broken.length, broken)

for (const rel of broken) {
  try {
    const original = execSync(`git show HEAD:forum-vue/front/${rel}`, { cwd: ROOT, encoding: 'utf8' })
    const scriptMatch = original.match(/<script setup>\s*([\s\S]*?)<\/script>/)
    if (!scriptMatch) {
      console.log('skip no script', rel)
      continue
    }
    const template = original.slice(0, scriptMatch.index).trimEnd()
    const inlineStyle = original.match(/<style[^>]*>[\s\S]*?<\/style>/)
    const externalStyle = original.match(/<style[^>]*src=[^>]*><\/style>/)
    let scriptSrc
    const s = scriptMatch[1]
    const m = s.match(/from\s+['"](@scripts\/[^'"]+)['"]/)
    if (m) scriptSrc = `@scripts/${m[1].slice('@scripts/'.length).replace(/\.js$/, '')}.js`
    else if (rel === 'src/App.vue') scriptSrc = '@scripts/App.js'
    else {
      const base = path.basename(rel, '.vue')
      if (rel.includes('/views/')) scriptSrc = `@scripts/views/${base}.js`
      else if (rel.includes('/components/')) {
        const sub = rel.replace(/^src\/components\//, '').replace('.vue', '.js')
        scriptSrc = `@scripts/components/${sub}`
      } else continue
    }
    let styleLine = externalStyle ? `\n${externalStyle[0]}` : ''
    if (!styleLine && inlineStyle) {
      const cssPath = rel.replace('.vue', '.css').replace(/^src\//, 'src/')
      const cssAbs = path.join(ROOT, cssPath)
      if (!fs.existsSync(cssAbs)) {
        const inner = inlineStyle[0].match(/<style[^>]*>([\s\S]*?)<\/style>/)?.[1]?.trim()
        if (inner) fs.writeFileSync(cssAbs, `${inner}\n`)
      }
      styleLine = `\n<style scoped src="./${path.basename(cssPath)}"></style>`
    }
    fs.writeFileSync(path.join(ROOT, rel), `${template}\n\n<script setup src="${scriptSrc}"></script>${styleLine}\n`)
    console.log('fixed', rel)
  } catch (e) {
    console.log('no git', rel)
  }
}
