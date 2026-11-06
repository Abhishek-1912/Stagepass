import { expect, test } from 'vitest'
import { applicationSchema } from './applicationSchema'

const valid = {
  orgName: 'Acme Live',
  bio: '',
  contactEmail: 'hello@acme.test',
  contactPhone: '9876543210',
  website: '',
  pan: 'abcde1234f',
  gstin: '',
  state: 'Maharashtra',
  payoutAccountName: 'Acme Live LLP',
  payoutAccountNumber: '1234 5678 9012',
  payoutIfsc: 'hdfc0001234',
}

test('accepts a valid application and normalises identifiers', () => {
  const result = applicationSchema.parse(valid)
  expect(result.pan).toBe('ABCDE1234F')
  expect(result.payoutIfsc).toBe('HDFC0001234')
  expect(result.payoutAccountNumber).toBe('123456789012')
  expect(result.gstin).toBeUndefined()
  expect(result.website).toBeUndefined()
})

test('rejects bad PAN, IFSC, phone and account number', () => {
  const result = applicationSchema.safeParse({
    ...valid,
    pan: '123',
    payoutIfsc: 'SBIN123',
    contactPhone: '12345',
    payoutAccountNumber: '12',
  })
  expect(result.success).toBe(false)
  const fields = result.error!.issues.map((i) => i.path[0])
  expect(fields).toEqual(expect.arrayContaining(['pan', 'payoutIfsc', 'contactPhone', 'payoutAccountNumber']))
})

test('validates GSTIN only when given', () => {
  expect(applicationSchema.safeParse({ ...valid, gstin: '27ABCDE1234F1Z5' }).success).toBe(true)
  expect(applicationSchema.safeParse({ ...valid, gstin: 'NOPE' }).success).toBe(false)
})
