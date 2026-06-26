import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const SRC = path.resolve(__dirname, '../src')

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

function scriptSrcToAbs(src) {
  if (src.startsWith('@scripts/')) return path.join(SRC, 'scripts', src.slice('@scripts/'.length))
  if (src.startsWith('@/')) return path.join(SRC, src.slice(2))
  return null
}

function parseHeadBindings(head) {
  const names = new Set()
  for (const m of head.matchAll(/import\s+(?:{([^}]+)}|(\w+))\s+from/g)) {
    if (m[1]) {
      m[1].split(',').forEach((part) => {
        const n = part.trim().split(/\s+as\s+/).pop().trim()
        if (n) names.add(n)
      })
    } else if (m[2]) names.add(m[2])
  }
  for (const m of head.matchAll(/\b(?:const|let)\s+(\w+)/g)) names.add(m[1])
  for (const m of head.matchAll(/\bfunction\s+(\w+)/g)) names.add(m[1])
  return names
}

const targets = new Set()
for (const vue of walk(SRC)) {
  const m = fs.readFileSync(vue, 'utf8').match(/<script setup src="([^"]+)"/)
  if (!m) continue
  const abs = scriptSrcToAbs(m[1]) || path.resolve(path.dirname(vue), m[1].replace(/^\.\//, ''))
  if (abs && fs.existsSync(abs)) targets.add(abs)
}

let fixed = 0
for (const file of targets) {
  let code = fs.readFileSync(file, 'utf8')
  const next = code
    .replace(/^export\s+(?=const|let|function use)/gm, '')
    .replace(/\bexport\s+function\s+use/g, 'function use')

  const entryMatch = next.match(/(\nimport[\s\S]*?\nconst\s*\{[\s\S]*?\}\s*=\s*use\w+\([\s\S]*?\))\s*$/m)
  if (!entryMatch) {
    if (next !== code) {
      fs.writeFileSync(file, next)
      fixed++
    }
    continue
  }

  const entryStart = next.lastIndexOf('\nimport')
  const head = next.slice(0, entryStart)
  const entry = next.slice(entryStart + 1)
  const fnIdx = next.search(/\nfunction use\w+\(/)
  const headForBindings = fnIdx >= 0 ? next.slice(0, fnIdx) : head
  const headBindings = parseHeadBindings(headForBindings)

  const imports = entry.match(/^import[\s\S]*?(?=\nconst\s*\{)/)?.[0]?.trim() || ''
  const dm = entry.match(/const\s*\{([\s\S]*?)\}\s*=\s*(use\w+\([\s\S]*?\))\s*$/m)
  if (!dm) continue

  const kept = dm[1]
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .filter((name) => !headBindings.has(name))

  let rebuilt = head.trimEnd()
  if (imports) rebuilt += `\n\n${imports}`
  if (kept.length) rebuilt += `\n\nconst {\n  ${kept.join(',\n  ')},\n} = ${dm[2].trim()}`
  rebuilt += '\n'

  if (rebuilt !== code) {
    fs.writeFileSync(file, rebuilt)
    fixed++
  }
}

console.log(`processed ${targets.size} setup scripts, fixed ${fixed}`)
