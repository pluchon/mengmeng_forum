import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const SCRIPTS = path.resolve(__dirname, '../src/scripts')

function walk(dir, acc = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name)
    if (fs.statSync(full).isDirectory()) walk(full, acc)
    else if (name.endsWith('.js')) acc.push(full)
  }
  return acc
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

let fixed = 0
for (const file of walk(SCRIPTS)) {
  let code = fs.readFileSync(file, 'utf8')
  const idx = code.search(/\nimport[\s\S]*\nconst\s*\{[\s\S]*\}\s*=\s*use\w+\(/)
  if (idx < 0) continue

  const head = code.slice(0, idx)
  const entry = code.slice(idx + 1)
  const headBindings = parseHeadBindings(head)

  const m = entry.match(/const\s*\{([\s\S]*?)\}\s*=\s*(use\w+\([\s\S]*?\))\s*$/m)
  if (!m) continue

  const kept = m[1]
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .filter((name) => !headBindings.has(name))

  const imports = entry.match(/^import[\s\S]*?(?=\nconst\s*\{)/)?.[0]?.trim() || ''
  const call = m[2].trim()

  let newEntry = imports
  if (kept.length) {
    newEntry += `${newEntry ? '\n\n' : ''}const {\n  ${kept.join(',\n  ')},\n} = ${call}`
  } else if (call) {
    newEntry += `${newEntry ? '\n\n' : ''}${call}`
  }

  const merged = `${head.trimEnd()}\n\n${newEntry.trim()}\n`
  if (merged !== code) {
    fs.writeFileSync(file, merged)
    fixed++
  }
}

console.log(`fixed ${fixed} entry blocks`)
