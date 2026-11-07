// Fails the build when the JavaScript loaded by the first page (entry chunk + its static imports) is over budget.
import { readFileSync } from 'node:fs'
import { gzipSync } from 'node:zlib'
import path from 'node:path'

const BUDGET_KB = 350
const dist = path.resolve(import.meta.dirname, '..', 'dist')
const manifest = JSON.parse(readFileSync(path.join(dist, '.vite', 'manifest.json'), 'utf8'))
const entry = Object.values(manifest).find((c) => c.isEntry)
if (!entry) throw new Error('No entry chunk in dist/.vite/manifest.json - run `npm run build` first')

const seen = new Set()
function collect(chunk) {
  if (seen.has(chunk.file)) return
  seen.add(chunk.file)
  for (const key of chunk.imports ?? []) collect(manifest[key])
}
collect(entry)

let total = 0
for (const file of seen) {
  const size = gzipSync(readFileSync(path.join(dist, file))).length
  total += size
  console.log(`${(size / 1024).toFixed(1).padStart(8)} kB  ${file}`)
}
const kb = total / 1024
console.log(`${kb.toFixed(1).padStart(8)} kB  initial JS (gzip), budget ${BUDGET_KB} kB`)
if (kb > BUDGET_KB) {
  console.error(`Initial JS is ${kb.toFixed(1)} kB gzip - over the ${BUDGET_KB} kB budget.`)
  process.exit(1)
}
