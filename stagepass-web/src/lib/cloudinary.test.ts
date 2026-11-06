import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { type UploadSignature, uploadImage } from './cloudinary'

const sig: UploadSignature = {
  cloudName: 'demo',
  apiKey: '1234',
  timestamp: 1700000000,
  signature: 'abc',
  folder: 'stagepass/events/e1',
  allowedFormats: 'jpg,png,webp',
  uploadUrl: 'https://api.cloudinary.com/v1_1/demo/image/upload',
  maxBytes: 5 * 1024 * 1024,
}

const fetchMock = vi.fn()
beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
})
afterEach(() => vi.unstubAllGlobals())

const file = (bytes: number, type = 'image/jpeg', name = 'cover.jpg') =>
  new File([new Uint8Array(bytes)], name, { type })

test('refuses oversized files without calling the network', async () => {
  await expect(uploadImage(file(6 * 1024 * 1024), sig)).rejects.toThrow(/5 MB/)
  expect(fetchMock).not.toHaveBeenCalled()
})

test('refuses formats outside the allowed list', async () => {
  await expect(uploadImage(file(10, 'image/gif', 'a.gif'), sig)).rejects.toThrow(/JPG, PNG or WebP/i)
  expect(fetchMock).not.toHaveBeenCalled()
})

test('posts the signed form and returns secure_url', async () => {
  fetchMock.mockResolvedValue(
    new Response(JSON.stringify({ secure_url: 'https://res.cloudinary.com/demo/image/upload/v1/stagepass/events/e1/x.jpg' }), {
      status: 200,
    }),
  )

  const url = await uploadImage(file(10), sig)

  expect(url).toBe('https://res.cloudinary.com/demo/image/upload/v1/stagepass/events/e1/x.jpg')
  const [endpoint, init] = fetchMock.mock.calls[0]
  expect(endpoint).toBe(sig.uploadUrl)
  const form = init.body as FormData
  expect(form.get('api_key')).toBe('1234')
  expect(form.get('signature')).toBe('abc')
  expect(form.get('folder')).toBe('stagepass/events/e1')
  expect(form.get('allowed_formats')).toBe('jpg,png,webp')
  expect(form.get('timestamp')).toBe('1700000000')
})

test('surfaces Cloudinary error messages', async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ error: { message: 'Invalid Signature' } }), { status: 401 }))
  await expect(uploadImage(file(10), sig)).rejects.toThrow(/Invalid Signature/)
})
