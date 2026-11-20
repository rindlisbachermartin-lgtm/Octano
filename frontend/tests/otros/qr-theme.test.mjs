import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { parse, compileStyle } from '@vue/compiler-sfc'

test('el CSS compilado aplica fondos oscuros a la ficha y etiquetas, sin perder el selector del componente', () => {
  for (const [path, selectors] of [
    ['app/components/qr/FichaPublica.vue', ['html.dark .sheet-background', 'html.dark .vehicle-single-card']],
    ['app/components/qr/PanelApi.vue', ['html.dark .sticker-card']],
  ]) {
    const { descriptor } = parse(readFileSync(path, 'utf8'))
    const result = compileStyle({ source: descriptor.styles[0].content, filename: path, id: 'data-v-test', scoped: true })
    assert.deepEqual(result.errors, [])
    for (const selector of selectors) assert.ok(result.code.includes(`${selector} { background: #`), selector)
    assert.doesNotMatch(result.code, /(?:^|\n)html\.dark\s*\{/)
  }
})

test('el fondo del código en la plantilla no se aplica al formulario de vinculación', () => {
  const path = 'app/pages/codigos-qr.vue'
  const { descriptor } = parse(readFileSync(path, 'utf8'))
  const { code, errors } = compileStyle({ source: descriptor.styles[0].content, filename: path, id: 'data-v-test', scoped: true })
  assert.deepEqual(errors, [])
  assert.ok(code.includes('html.dark .qr-template-page .sticker-code'))
  assert.doesNotMatch(code, /html\.dark\s+\.sticker-code\s*\{/)
})
