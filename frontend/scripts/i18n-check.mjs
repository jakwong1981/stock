#!/usr/bin/env node
// Fails (exit 1) when the four locale files do not have exactly the same keys,
// or when a message is empty (AGENT.MD §4.5).
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const dir = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'locales')
const LOCALES = ['en-US', 'zh-HK', 'zh-TW', 'zh-CN']

function flatten(obj, prefix = '', out = new Map()) {
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k
    if (v && typeof v === 'object') flatten(v, key, out)
    else out.set(key, v)
  }
  return out
}

const maps = Object.fromEntries(LOCALES.map((l) => [l, flatten(JSON.parse(readFileSync(join(dir, `${l}.json`), 'utf8')))]))
const allKeys = new Set(LOCALES.flatMap((l) => [...maps[l].keys()]))
let problems = 0
for (const key of [...allKeys].sort()) {
  for (const l of LOCALES) {
    const v = maps[l].get(key)
    if (v === undefined) {
      console.error(`✗ ${l}: missing "${key}"`)
      problems++
    } else if (typeof v !== 'string' || v.trim() === '') {
      console.error(`✗ ${l}: empty "${key}"`)
      problems++
    } else if (v.includes('@')) {
      // vue-i18n treats "@" as linked-message syntax
      console.error(`✗ ${l}: "${key}" contains "@" (escape as {'@'})`)
      problems++
    }
  }
}
// Placeholders must match across locales
for (const key of allKeys) {
  const ph = (s) => [...String(s ?? '').matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join(',')
  const ref = ph(maps['en-US'].get(key))
  for (const l of LOCALES) {
    if (maps[l].has(key) && ph(maps[l].get(key)) !== ref) {
      console.error(`✗ ${l}: placeholders differ for "${key}"`)
      problems++
    }
  }
}
if (problems) {
  console.error(`\n${problems} i18n problem(s) found.`)
  process.exit(1)
}
console.log(`✓ ${allKeys.size} keys consistent across ${LOCALES.join(', ')}`)
