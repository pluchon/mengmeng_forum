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

let n = 0
for (const file of walk(SCRIPTS)) {
  const rel = path.relative(SCRIPTS, file).replace(/\\/g, '/')
  const bases = [`@scripts/${rel.replace(/\.js$/, '')}`, `@scripts/${rel}`]
  let code = fs.readFileSync(file, 'utf8')
  let next = code
  for (const self of bases) {
    const esc = self.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    next = next.replace(new RegExp(`^import\\s+\\{[^}]*\\}\\s+from\\s+['"]${esc}['"]\\s*\\n`, 'gm'), '')
  }
  if (next !== code) {
    fs.writeFileSync(file, next)
    n++
  }
}
console.log(`removed self-imports from ${n} files`)
