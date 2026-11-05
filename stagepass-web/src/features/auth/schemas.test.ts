import { expect, test } from 'vitest'
import { loginSchema, registerSchema, resetSchema } from './schemas'

const validRegistration = {
  fullName: 'Asha Rao',
  email: 'asha@example.in',
  password: 'concert2026',
  confirmPassword: 'concert2026',
}

test('register accepts a valid form', () => {
  expect(registerSchema.safeParse(validRegistration).success).toBe(true)
})

test('register rejects mismatched confirm', () => {
  const result = registerSchema.safeParse({ ...validRegistration, confirmPassword: 'different1' })
  expect(result.success).toBe(false)
  expect(result.error?.issues[0].path).toEqual(['confirmPassword'])
})

test('register rejects password without digit', () => {
  const result = registerSchema.safeParse({ ...validRegistration, password: 'onlyletters', confirmPassword: 'onlyletters' })
  expect(result.success).toBe(false)
  expect(result.error?.issues.some((i) => i.path[0] === 'password')).toBe(true)
})

test('login rejects short password and bad email', () => {
  const result = loginSchema.safeParse({ email: 'not-an-email', password: 'short' })
  expect(result.success).toBe(false)
  expect(result.error?.issues.map((i) => i.path[0]).sort()).toEqual(['email', 'password'])
})

test('reset requires matching passwords', () => {
  expect(resetSchema.safeParse({ password: 'newpass123', confirmPassword: 'newpass124' }).success).toBe(false)
})
