import { readFileSync } from 'node:fs'
import path from 'node:path'

/**
 * The enforced website CSP from render.yaml, adapted for the test: the fake API host (http://api.test) is allowed
 * where the real API host would be, and report-uri is dropped (there is no report collector in the test).
 */
export function testCsp(): string {
  const yaml = readFileSync(path.resolve(__dirname, '..', '..', '..', 'render.yaml'), 'utf8').split(/\r?\n/)
  let inWeb = false
  for (let i = 0; i < yaml.length; i++) {
    if (yaml[i].startsWith('  - type:')) inWeb = false
    if (yaml[i].trim() === 'name: stagepass-web') inWeb = true
    if (inWeb && yaml[i].trim() === 'name: Content-Security-Policy') {
      const value = yaml[i + 1].trim().replace(/^value: /, '').replace(/^"|"$/g, '')
      return value
        .split(';')
        .map((d) => d.trim())
        .filter((d) => d && !d.startsWith('report-uri'))
        .map((d) => (d.startsWith('connect-src') ? `${d} http://api.test` : d))
        .join('; ')
    }
  }
  throw new Error('render.yaml has no enforced Content-Security-Policy for stagepass-web')
}
