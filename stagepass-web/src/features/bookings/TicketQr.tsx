import QRCode from 'qrcode'
import { useEffect, useState } from 'react'
import { cn } from '@/lib/utils'

/** The ticket's signed entry code as a QR image, drawn in the browser (nothing leaves the device). */
export function TicketQr({ token, size, label, className }: { token: string; size: number; label: string; className?: string }) {
  const [src, setSrc] = useState<{ token: string; url: string } | null>(null)

  useEffect(() => {
    let live = true
    QRCode.toDataURL(token, { errorCorrectionLevel: 'M', margin: 1, width: Math.max(320, size * 2) })
      .then((url) => live && setSrc({ token, url }))
      .catch(() => live && setSrc(null))
    return () => {
      live = false
    }
  }, [token, size])

  if (!src || src.token !== token) {
    return <div role="status" aria-label="Preparing QR code" style={{ width: size, height: size }} className={cn('animate-pulse rounded-xl bg-muted', className)} />
  }
  return <img src={src.url} alt={label} width={size} height={size} className={cn('rounded-xl bg-white p-2', className)} />
}
