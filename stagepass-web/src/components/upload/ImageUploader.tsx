import { ImagePlus, Loader2, X } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { requestUploadSignature } from '@/features/organizer/api'
import { isApiError } from '@/lib/api/ApiError'
import type { UploadPurpose } from '@/lib/api/types'
import { uploadImage } from '@/lib/cloudinary'
import { cn } from '@/lib/utils'

interface Props {
  purpose: UploadPurpose
  eventId?: string
  value?: string | null
  onChange: (url: string | null) => void
  label: string
  hint?: string
  aspect?: 'cover' | 'square'
  disabled?: boolean
}

/** Picks an image, gets a signature from the API, uploads straight to Cloudinary and reports the URL. */
export function ImageUploader({ purpose, eventId, value, onChange, label, hint, aspect = 'cover', disabled }: Props) {
  const inputId = useId()
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handle(file: File | undefined) {
    if (!file) return
    setBusy(true)
    setError(null)
    try {
      const signature = await requestUploadSignature(purpose, eventId)
      onChange(await uploadImage(file, signature))
    } catch (e) {
      setError(
        isApiError(e) && e.code === 'SERVICE_UNAVAILABLE'
          ? 'Image uploads aren’t set up yet. Ask an admin to add the Cloudinary keys.'
          : e instanceof Error
            ? e.message
            : 'Upload failed',
      )
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
    }
  }

  return (
    <div className="space-y-1.5">
      <label htmlFor={inputId} className="text-sm font-medium">
        {label}
      </label>
      <div
        className={cn(
          'relative overflow-hidden rounded-2xl border border-dashed border-border bg-muted/40',
          aspect === 'cover' ? 'aspect-[16/9]' : 'aspect-square w-32',
        )}
      >
        {value ? (
          <>
            <img src={value} alt="" className="size-full object-cover" />
            {!disabled && (
              <Button
                type="button"
                size="icon"
                variant="secondary"
                aria-label={`Remove ${label.toLowerCase()}`}
                className="absolute right-2 top-2 size-8 rounded-full"
                onClick={() => onChange(null)}
              >
                <X className="size-4" />
              </Button>
            )}
          </>
        ) : (
          <button
            type="button"
            disabled={busy || disabled}
            onClick={() => input.current?.click()}
            className="flex size-full flex-col items-center justify-center gap-2 text-sm text-muted-foreground transition-colors hover:bg-muted"
          >
            {busy ? <Loader2 className="size-6 animate-spin" /> : <ImagePlus className="size-6" />}
            <span>{busy ? 'Uploading…' : 'Choose image'}</span>
          </button>
        )}
        <input
          ref={input}
          id={inputId}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          className="sr-only"
          disabled={busy || disabled}
          onChange={(e) => handle(e.target.files?.[0])}
        />
      </div>
      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : (
        hint && <p className="text-xs text-muted-foreground">{hint}</p>
      )}
    </div>
  )
}
