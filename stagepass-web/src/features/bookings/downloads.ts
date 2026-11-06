import { apiFile } from '@/lib/api/client'

/** Downloads an API file (ticket or invoice PDF) and saves it under the server's filename. */
export async function downloadFile(path: string, fallbackName: string): Promise<void> {
  const { blob, filename } = await apiFile(path)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename ?? fallbackName
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
