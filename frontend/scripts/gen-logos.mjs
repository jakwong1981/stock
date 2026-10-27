#!/usr/bin/env node
// Generates neon monogram placeholder logos into public/logos/.
// Official brand logos are trademarks: replace these files with assets obtained from a
// licensed logo provider (e.g. Logo.dev) and cache them here or on your own CDN.
import { mkdirSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const out = join(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'logos')
mkdirSync(out, { recursive: true })

const SYMBOLS = ['AAPL', 'NVDA', 'TSLA', 'MSFT', 'AMZN', 'GOOGL', 'META', 'JPM',
  '0700.HK', '9988.HK', '3690.HK', '0005.HK', '1810.HK']
const PALETTE = ['#00f0ff', '#bd00ff', '#f3e600', '#22f59a', '#ff3b6b']

SYMBOLS.forEach((s, i) => {
  const color = PALETTE[i % PALETTE.length]
  const label = s.replace(/\.HK$/, '').replace(/^0+/, '').slice(0, 4)
  const size = label.length > 3 ? 18 : 22
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" role="img" aria-label="${s}">
  <rect x="2" y="2" width="60" height="60" rx="12" fill="#0a0b10" stroke="${color}" stroke-width="2.5"/>
  <text x="32" y="39" text-anchor="middle" font-family="Menlo,Consolas,monospace" font-size="${size}" font-weight="700" fill="${color}">${label}</text>
</svg>
`
  writeFileSync(join(out, `${s}.svg`), svg)
})
console.log(`Generated ${SYMBOLS.length} logos in ${out}`)
