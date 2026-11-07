import { Camera, Trash2 } from 'lucide-react'
import { useRef, useState } from 'react'
import { toast } from 'sonner'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { useUpdateMe, type Me } from '@/features/auth/useMe'
import { isApiError } from '@/lib/api/ApiError'
import { api } from '@/lib/api/client'
import { uploadImage, type UploadSignature } from '@/lib/cloudinary'
import { cloudinary } from '@/lib/images'

function initials(me: Me): string {
  const source = me.fullName ?? me.email ?? '?'
  return source
    .split(/\s+/)
    .map((p) => p[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()
}

/** Profile photo: uploads straight to our Cloudinary folder, then saves the URL on the profile. */
export function AvatarUpload({ me }: { me: Me }) {
  const input = useRef<HTMLInputElement>(null)
  const update = useUpdateMe()
  const [busy, setBusy] = useState(false)

  async function onFile(file: File | undefined) {
    if (!file) return
    setBusy(true)
    try {
      const sig = await api<UploadSignature>('/uploads/signature', { method: 'POST', json: { purpose: 'AVATAR' } })
      const url = await uploadImage(file, sig)
      await update.mutateAsync({ avatarUrl: url })
      toast.success('Photo updated')
    } catch (e) {
      toast.error(isApiError(e) ? e.detail : e instanceof Error ? e.message : 'Could not upload the photo')
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
    }
  }

  async function remove() {
    setBusy(true)
    try {
      await update.mutateAsync({ avatarUrl: '' })
    } catch (e) {
      toast.error(isApiError(e) ? e.detail : 'Could not remove the photo')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex items-center gap-4">
      <Avatar className="size-20">
        {me.avatarUrl && <AvatarImage src={cloudinary(me.avatarUrl, 'f_auto,q_auto,w_160,h_160,c_fill')} alt="" />}
        <AvatarFallback className="bg-gradient-brand text-xl font-semibold text-white">{initials(me)}</AvatarFallback>
      </Avatar>
      <div className="flex flex-wrap gap-2">
        <input
          ref={input}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          className="sr-only"
          aria-label="Choose a profile photo"
          onChange={(e) => onFile(e.target.files?.[0])}
        />
        <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => input.current?.click()}>
          <Camera /> {busy ? 'Uploading…' : me.avatarUrl ? 'Change photo' : 'Add photo'}
        </Button>
        {me.avatarUrl && (
          <Button type="button" variant="ghost" size="sm" disabled={busy} onClick={remove}>
            <Trash2 /> Remove
          </Button>
        )}
      </div>
    </div>
  )
}
