/** Signature issued by the Stagepass API for one direct upload to Cloudinary. */
export interface UploadSignature {
  cloudName: string
  apiKey: string
  timestamp: number
  signature: string
  folder: string
  allowedFormats: string
  uploadUrl: string
  maxBytes: number
}

const MIME_BY_FORMAT: Record<string, string[]> = {
  jpg: ['image/jpeg'],
  png: ['image/png'],
  webp: ['image/webp'],
}

/** Uploads one image straight from the browser to Cloudinary and returns its https URL. */
export async function uploadImage(file: File, sig: UploadSignature): Promise<string> {
  const allowed = sig.allowedFormats.split(',').flatMap((f) => MIME_BY_FORMAT[f.trim()] ?? [])
  if (!allowed.includes(file.type)) {
    throw new Error('Please choose a JPG, PNG or WebP image.')
  }
  if (file.size > sig.maxBytes) {
    throw new Error(`Images must be ${Math.round(sig.maxBytes / (1024 * 1024))} MB or smaller.`)
  }

  const form = new FormData()
  form.append('file', file)
  form.append('api_key', sig.apiKey)
  form.append('timestamp', String(sig.timestamp))
  form.append('signature', sig.signature)
  form.append('folder', sig.folder)
  form.append('allowed_formats', sig.allowedFormats)

  const response = await fetch(sig.uploadUrl, { method: 'POST', body: form })
  const body = (await response.json().catch(() => ({}))) as { secure_url?: string; error?: { message?: string } }
  if (!response.ok || !body.secure_url) {
    throw new Error(body.error?.message ?? 'The image could not be uploaded. Please try again.')
  }
  return body.secure_url
}
