/**
 * 清理 __vue_entry__ 中的自引用 import 与重复声明。
 */
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

let fixed = 0
for (const file of walk(SCRIPTS)) {
  let code = fs.readFileSync(file, 'utf8')
  if (!code.includes('// __vue_entry__')) continue

  const [head, entry] = code.split('// __vue_entry__\n')
  const headImports = parseImports(head)
  const rel = path.relative(SCRIPTS, file).replace(/\\/g, '/')
  const selfScriptsPath = `@scripts/${rel}`

  let entryLines = entry.split('\n')
  const newEntry = []
  let destructureLines = []
  let inDestructure = false
  let destructureBuf = ''

  for (const line of entryLines) {
    if (inDestructure) {
      destructureBuf += `${line}\n`
      if (line.includes('} = use')) {
        inDestructure = false
        const m = destructureBuf.match(/const\s*\{([^}]*)\}\s*=\s*use\w+\([^)]*\)/s)
        if (m) {
          const kept = m[1]
            .split(',')
            .map((s) => s.trim())
            .filter(Boolean)
            .filter((name) => !headImports.has(name))
          if (kept.length) {
            const call = destructureBuf.match(/=\s*(use\w+\([^)]*\))/s)?.[1] || 'useX()'
            newEntry.push(`const {\n  ${kept.join(',\n  ')},\n} = ${call}`)
          }
        } else {
          newEntry.push(destructureBuf.trimEnd())
        }
        destructureBuf = ''
      }
      continue
    }

    const importFromSelf = line.match(new RegExp(`import\\s+\\{[^}]*\\}\\s+from\\s+['"]${selfScriptsPath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}['"]`))
    if (importFromSelf) continue

    const importNamed = line.match(/^import\s+\{([^}]+)\}\s+from/)
    if (importNamed) {
      const parts = importNamed[1].split(',').map((p) => p.trim()).filter(Boolean)
      const kept = parts.filter((part) => {
        const name = part.split(/\s+as\s+/).pop().trim()
        return !headImports.has(name)
      })
      if (kept.length === 0) continue
      const from = line.match(/from\s+['"][^'"]+['"]/)?.[0]
      newEntry.push(`import { ${kept.join(', ')} } ${from}`)
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

    if (line.trim()) newEntry.push(line)
  }

  const merged = `${head.trimEnd()}\n\n${newEntry.join('\n').trim()}\n`
  fs.writeFileSync(file, merged, 'utf8')
  fixed++
}

console.log(`fixed ${fixed} script entry files`)
