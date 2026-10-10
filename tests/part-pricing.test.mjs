import assert from 'node:assert/strict'
import { test } from 'node:test'
import { partSalePrice, partMargin } from '../app/utils/partPricing.ts'

test('sale price uses margin over cost, including fractional margins and cents', () => {
  assert.equal(partSalePrice(10000, 30), 13000)
  assert.equal(partSalePrice(8500, 0), 8500)
  assert.equal(partSalePrice(123.45, 12.5), 138.88)
  assert.equal(partSalePrice(0, 50), 0)
})

test('existing prices retain their value when their margin is recovered', () => {
  for (const [cost, price] of [[8500, 14500], [38000, 62000], [123.45, 138.88]]) {
    assert.equal(partSalePrice(cost, partMargin(cost, price)), price)
  }
  assert.equal(partMargin(0, 0), 0)
})

test('invalid cost or margin cannot produce a valid sale price', () => {
  for (const [cost, margin] of [[-1, 30], [10, -1], [NaN, 30], [10, Infinity]]) {
    assert.equal(Number.isNaN(partSalePrice(cost, margin)), true)
  }
})
