import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { ImageUploader } from '@/components/upload/ImageUploader'
import { isApiError } from '@/lib/api/ApiError'
import type { EventDetail } from '@/lib/api/types'
import { useUpdateEvent } from '../../api'

const MAX_GALLERY = 8

export function MediaStep({ event }: { event: EventDetail }) {
  const navigate = useNavigate()
  const update = useUpdateEvent(event.id)

  async function save(input: { coverImageUrl?: string; gallery?: string[] }) {
    try {
      await update.mutateAsync(input)
    } catch (e) {
      toast.error(isApiError(e) ? (e.fieldErrors[0]?.message ?? e.detail) : 'Could not save the image')
    }
  }

  return (
    <div className="max-w-3xl space-y-8">
      <ImageUploader
        purpose="EVENT_IMAGE"
        eventId={event.id}
        label="Cover image"
        hint="16:9, at least 1600×900, JPG/PNG/WebP up to 5 MB. This is the first thing people see."
        value={event.coverImageUrl}
        onChange={(url) => url && save({ coverImageUrl: url })}
      />

      <div className="space-y-3">
        <div>
          <h3 className="font-sans text-base font-semibold tracking-normal">Gallery</h3>
          <p className="text-sm text-muted-foreground">Up to {MAX_GALLERY} photos of past editions, the venue or artists.</p>
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {event.gallery.map((url) => (
            <ImageUploader
              key={url}
              purpose="EVENT_IMAGE"
              eventId={event.id}
              label="Gallery photo"
              value={url}
              onChange={() => save({ gallery: event.gallery.filter((g) => g !== url) })}
            />
          ))}
          {event.gallery.length < MAX_GALLERY && (
            <ImageUploader
              key={`add-${event.gallery.length}`}
              purpose="EVENT_IMAGE"
              eventId={event.id}
              label="Add photo"
              onChange={(url) => url && save({ gallery: [...event.gallery, url] })}
            />
          )}
        </div>
      </div>

      <div className="flex justify-between">
        <Button variant="ghost" onClick={() => navigate(`/organizer/events/${event.id}/tickets`)}>
          Back
        </Button>
        <Button size="lg" className="rounded-full px-6" onClick={() => navigate(`/organizer/events/${event.id}/review`)}>
          Continue to review
        </Button>
      </div>
    </div>
  )
}
