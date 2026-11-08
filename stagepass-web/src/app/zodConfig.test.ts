import { expect, test } from 'vitest'
import { globalConfig } from 'zod/v4/core'
import './zodConfig'

test('zod runs without evaluating code (no CSP eval probe)', () => {
  expect(globalConfig.jitless).toBe(true)
})
