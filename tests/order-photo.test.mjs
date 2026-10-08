import assert from 'node:assert/strict'
import test from 'node:test'
import { prepareOrderPhoto } from '../app/utils/orderPhoto.ts'

test('phone photos larger than 2 MB are resized proportionally and converted to JPEG', async () => {
  const originals = { Image: globalThis.Image, document: globalThis.document, URL: globalThis.URL }
  let released = false
  const canvas = { width: 0, height: 0, getContext: () => ({ fillRect() {}, drawImage() {} }), toDataURL: (type, quality) => {
    assert.equal(type, 'image/jpeg')
    assert.equal(quality, 0.75)
    return 'data:image/jpeg;base64,test'
  } }
  globalThis.Image = class { naturalWidth = 4032; naturalHeight = 3024; set src(value) { this.onload() } }
  globalThis.document = { createElement: () => canvas }
  globalThis.URL = { createObjectURL: () => 'blob:photo', revokeObjectURL: () => { released = true } }
  try {
    assert.equal(await prepareOrderPhoto({ type: 'image/jpeg', size: 6 * 1024 * 1024, name: 'camera.jpg' }), 'data:image/jpeg;base64,test')
    assert.equal(canvas.width, 1280)
    assert.equal(canvas.height, 960)
    assert.equal(released, true)
  } finally { Object.assign(globalThis, originals) }
})

test('invalid images and oversized files return actionable errors', async () => {
  await assert.rejects(prepareOrderPhoto({ type: 'application/pdf', size: 123, name: 'file.pdf' }), /archivo de imagen/)
  await assert.rejects(prepareOrderPhoto({ type: 'image/jpeg', size: 21 * 1024 * 1024, name: 'photo.jpg' }), /20 MB/)
})

test('an unsupported phone format reports decoding failure and releases its blob URL', async () => {
  const originals = { Image: globalThis.Image, URL: globalThis.URL }
  let released = false
  globalThis.Image = class { set src(value) { this.onerror() } }
  globalThis.URL = { createObjectURL: () => 'blob:photo', revokeObjectURL: () => { released = true } }
  try {
    await assert.rejects(prepareOrderPhoto({ type: '', size: 100, name: 'phone.heic' }), /JPG o PNG/)
    assert.equal(released, true)
  } finally { Object.assign(globalThis, originals) }
})
