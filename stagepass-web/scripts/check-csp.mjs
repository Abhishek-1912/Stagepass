// Checks the built site against the enforced Content-Security-Policy in ../render.yaml:
//  - the policy is enforced (not Report-Only), allows the inline theme script's exact hash, and reports violations;
//  - no built JavaScript evaluates strings as code (the policy has no 'unsafe-eval').
import { createHash } from 'node:crypto'
import { readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const dist = path.join(root, 'dist')
const problems = []

export function webCsp(yamlText) {
  const lines = yamlText.split(/\r?\n/)
  let inWeb = false
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].startsWith('  - type:')) inWeb = false
    if (lines[i].trim() === 'name: stagepass-web') inWeb = true
    const name = lines[i].trim().match(/^name: (Content-Security-Policy(?:-Report-Only)?)$/)
    if (inWeb && name) {
      const value = lines[i + 1].trim().replace(/^value: /, '').replace(/^"|"$/g, '')
      return { header: name[1], value }
    }
  }
  return null
}

const csp = webCsp(readFileSync(path.join(root, '..', 'render.yaml'), 'utf8'))
if (!csp) problems.push('render.yaml: no Content-Security-Policy header for stagepass-web')
else {
  if (csp.header !== 'Content-Security-Policy') problems.push(`render.yaml: the header is ${csp.header}; it must be the enforced Content-Security-Policy`)
  // Google Maps (venue search + embeds) loads when VITE_GOOGLE_MAPS_KEY is set; keep its hosts allowed.
  const mapsHosts = ['https://maps.googleapis.com', 'https://places.googleapis.com', 'https://maps.gstatic.com', 'https://fonts.googleapis.com', 'https://fonts.gstatic.com']
  for (const needed of ['report-uri ', 'https://images.unsplash.com', "object-src 'none'", "frame-ancestors 'none'", ...mapsHosts]) {
    if (!csp.value.includes(needed)) problems.push(`render.yaml CSP is missing ${needed.trim()}`)
  }
  const html = readFileSync(path.join(dist, 'index.html'), 'utf8')
  for (const [, body] of html.matchAll(/<script>([\s\S]*?)<\/script>/g)) {
    const hash = `'sha256-${createHash('sha256').update(body, 'utf8').digest('base64')}'`
    if (!csp.value.includes(hash)) problems.push(`render.yaml CSP must allow the inline script in index.html: add ${hash} to script-src`)
  }
}

function jsFiles(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? jsFiles(path.join(dir, e.name)) : e.name.endsWith('.js') ? [path.join(dir, e.name)] : [],
  )
}
for (const file of jsFiles(path.join(dist, 'assets'))) {
  const code = readFileSync(file, 'utf8')
  if (/(?<![\w.$])eval\(|new Function\(/.test(code)) problems.push(`${path.relative(root, file)} evaluates code (eval / new Function), which the CSP blocks`)
}

if (problems.length) {
  for (const p of problems) console.error(`✗ ${p}`)
  process.exit(1)
}
console.log('✓ CSP is enforced, allows the inline theme script, reports violations, and no built code uses eval')
