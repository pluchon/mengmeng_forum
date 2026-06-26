/**
 * 将内嵌 <script setup> 接线为 <script setup src="...">（按 <script setup> 切分，避免误伤嵌套 template）
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const SRC = path.resolve(__dirname, '../src')

function walk(dir, acc = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name)
    const st = fs.statSync(full)
    if (st.isDirectory()) {
      if (name === 'node_modules') continue
      walk(full, acc)
    } else if (name.endsWith('.vue')) acc.push(full)
  }
  return acc
}

function scriptSrcToAbs(src) {
  if (src.startsWith('@scripts/')) return path.join(SRC, 'scripts', src.slice('@scripts/'.length))
  if (src.startsWith('@/')) return path.join(SRC, src.slice(2))
  return null
}

function resolveScriptSrc(vueFile, importPath) {
  if (importPath.startsWith('@scripts/')) {
    return `@scripts/${importPath.slice('@scripts/'.length).replace(/\.js$/, '')}.js`
  }
  if (importPath.startsWith('@/')) {
    const rel = importPath.slice(2)
    return `@/${rel.endsWith('.js') ? rel : `${rel}.js`}`
  }
  if (importPath.startsWith('.')) {
    const abs = path.resolve(path.dirname(vueFile), importPath)
    const rel = path.relative(SRC, abs).replace(/\\/g, '/')
    return `@/${rel.endsWith('.js') ? rel : `${rel}.js`}`
  }
  return null
}

function vueToDefaultScriptPath(vueFile) {
  const rel = path.relative(SRC, vueFile).replace(/\\/g, '/')
  if (rel === 'App.vue') return path.join(SRC, 'scripts/App.js')
  if (rel.startsWith('views/')) return path.join(SRC, 'scripts/views', `${path.basename(vueFile, '.vue')}.js`)
  if (rel.startsWith('components/')) {
    return path.join(SRC, 'scripts/components', rel.slice('components/'.length).replace('.vue', '.js'))
  }
  if (rel.startsWith('layouts/')) return path.join(SRC, 'scripts/layouts', `${path.basename(vueFile, '.vue')}.js`)
  return path.join(SRC, 'scripts', rel.replace('.vue', '.js'))
}

function stripExportFromComposable(js) {
  return js.replace(/\bexport\s+function\s+use/g, 'function use')
}

function findPrimaryScriptsImport(script) {
  const m = script.match(/from\s+['"](@scripts\/[^'"]+)['"]/)
  return m ? m[1] : null
}

function parseImports(code) {
  const names = new Set()
  for (const m of code.matchAll(/import\s+(?:{([^}]+)}|(\w+))\s+from/g)) {
    if (m[1]) {
      m[1].split(',').forEach((part) => {
        const n = part.trim().split(/\s+as\s+/).pop().trim()
        if (n) names.add(n)
      })
    } else if (m[2]) names.add(m[2])
  }
  return names
}

function cleanEntryBlock(head, entry, selfScriptsPath) {
  const headImports = parseImports(head)
  const lines = entry.split('\n')
  const out = []
  let destructureBuf = ''
  let inDestructure = false

  for (const line of lines) {
    if (inDestructure) {
      destructureBuf += `${line}\n`
      if (line.includes('} = use') || line.includes('} = use')) {
        inDestructure = false
        const m = destructureBuf.match(/const\s*\{([\s\S]*?)\}\s*=\s*(use\w+\([\s\S]*?\))/m)
        if (m) {
          const kept = m[1]
            .split(',')
            .map((s) => s.trim())
            .filter(Boolean)
            .filter((name) => !headImports.has(name))
          const call = m[2].trim()
          if (kept.length) out.push(`const {\n  ${kept.join(',\n  ')},\n} = ${call}`)
        } else {
          out.push(destructureBuf.trimEnd())
        }
        destructureBuf = ''
      }
      continue
    }

    if (line.match(new RegExp(`from\\s+['"]${selfScriptsPath.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&')}(?:\\.js)?['"]`))) {
      continue
    }

    const importNamed = line.match(/^import\s+\{([^}]+)\}\s+from/)
    if (importNamed) {
      const kept = importNamed[1]
        .split(',')
        .map((p) => p.trim())
        .filter(Boolean)
        .filter((part) => {
          const name = part.split(/\s+as\s+/).pop().trim()
          return !headImports.has(name)
        })
      if (!kept.length) continue
      const from = line.match(/from\s+['"][^'"]+['"]/)?.[0]
      out.push(`import { ${kept.join(', ')} } ${from}`)
      kept.forEach((part) => headImports.add(part.split(/\s+as\s+/).pop().trim()))
      continue
    }

    const importDefault = line.match(/^import\s+(\w+)\s+from/)
    if (importDefault && headImports.has(importDefault[1])) continue

    if (line.match(/^const\s*\{/)) {
      inDestructure = true
      destructureBuf = `${line}\n`
      continue
    }

    if (line.trim()) out.push(line)
  }

  return out.join('\n').trim()
}

const converted = []

for (const vueFile of walk(SRC)) {
  let vueContent = fs.readFileSync(vueFile, 'utf8')
  if (/<script\s+setup\s+src=/.test(vueContent)) continue

  const scriptMatch = vueContent.match(/<script setup>\s*([\s\S]*?)<\/script>/)
  if (!scriptMatch) continue

  const template = vueContent.slice(0, scriptMatch.index).trimEnd()
  const script = scriptMatch[1].trim()
  const afterScript = vueContent.slice(scriptMatch.index + scriptMatch[0].length)

  const inlineStyleMatch = afterScript.match(/<style scoped>\s*([\s\S]*?)<\/style>/)
  const externalStyleMatch = afterScript.match(/<style[^>]*src=[^>]*><\/style>/)

  const scriptsImport = findPrimaryScriptsImport(script)
  let scriptSrc
  let jsAbs

  if (scriptsImport) {
    scriptSrc = resolveScriptSrc(vueFile, scriptsImport)
    jsAbs = scriptSrcToAbs(scriptSrc)
  } else if (vueFile.endsWith(`${path.sep}App.vue`)) {
    scriptSrc = '@scripts/App.js'
    jsAbs = path.join(SRC, 'scripts/App.js')
  } else {
    jsAbs = vueToDefaultScriptPath(vueFile)
    const relFromSrc = path.relative(SRC, jsAbs).replace(/\\/g, '/')
    scriptSrc = relFromSrc.startsWith('scripts/')
      ? `@scripts/${relFromSrc.slice('scripts/'.length)}`
      : `@/${relFromSrc}`
  }

  const selfPath = scriptSrc.replace(/\.js$/, '')

  let jsContent = ''
  if (fs.existsSync(jsAbs)) {
    jsContent = stripExportFromComposable(fs.readFileSync(jsAbs, 'utf8'))
    if (/function use\w+\(/.test(jsContent)) {
      const cleanedEntry = cleanEntryBlock(jsContent, script, selfPath)
      if (cleanedEntry && !jsContent.includes(cleanedEntry)) {
        jsContent = `${jsContent.trimEnd()}\n\n${cleanedEntry}\n`
      }
    } else {
      jsContent = `${script}\n`
    }
  } else {
    jsContent = `${script}\n`
  }

  fs.mkdirSync(path.dirname(jsAbs), { recursive: true })
  fs.writeFileSync(jsAbs, jsContent, 'utf8')

  let styleLine = ''
  if (inlineStyleMatch) {
    const cssName = `${path.basename(vueFile, '.vue')}.css`
    let cssAbs
    const rel = path.relative(SRC, vueFile).replace(/\\/g, '/')
    if (rel.startsWith('views/')) cssAbs = path.join(path.dirname(vueFile), cssName)
    else if (rel.startsWith('layouts/')) cssAbs = path.join(path.dirname(vueFile), cssName)
    else if (rel === 'App.vue') cssAbs = path.join(SRC, cssName)
    else cssAbs = path.join(path.dirname(vueFile), cssName)

    if (!fs.existsSync(cssAbs)) fs.writeFileSync(cssAbs, `${inlineStyleMatch[1].trim()}\n`, 'utf8')
    styleLine = `\n<style scoped src="./${cssName}"></style>`
  } else if (externalStyleMatch) {
    styleLine = `\n${externalStyleMatch[0]}`
  }

  const newVue = `${template}\n\n<script setup src="${scriptSrc}"></script>${styleLine}\n`
  fs.writeFileSync(vueFile, newVue, 'utf8')
  converted.push(path.relative(SRC, vueFile))
}

console.log(`converted ${converted.length} vue files`)
