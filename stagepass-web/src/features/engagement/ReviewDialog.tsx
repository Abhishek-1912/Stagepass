import { Star } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { isApiError } from '@/lib/api/ApiError'
import { cn } from '@/lib/utils'
import { useDeleteReview, useSaveReview, type PublicReview } from './reviewsApi'

/** Write or edit your review: 1–5 stars (radio buttons) and an optional comment. */
export function ReviewDialog({ eventId, existing }: { eventId: string; existing: PublicReview | null }) {
  const save = useSaveReview()
  const del = useDeleteReview()
  const [open, setOpen] = useState(false)
  const [rating, setRating] = useState(existing?.rating ?? 0)
  const [comment, setComment] = useState(existing?.comment ?? '')
  const busy = save.isPending || del.isPending

  async function submit() {
    try {
      await save.mutateAsync({ eventId, rating, comment: comment.trim() })
      toast.success(existing ? 'Review updated' : 'Thanks for your review')
      setOpen(false)
    } catch (e) {
      toast.error(isApiError(e) ? e.detail : 'Could not save your review')
    }
  }

  async function remove() {
    try {
      await del.mutateAsync(eventId)
      toast.success('Review deleted')
      setOpen(false)
    } catch (e) {
      toast.error(isApiError(e) ? e.detail : 'Could not delete your review')
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        setOpen(o)
        if (o) {
          setRating(existing?.rating ?? 0)
          setComment(existing?.comment ?? '')
        }
      }}
    >
      <DialogTrigger asChild>
        <Button variant={existing ? 'outline' : 'default'}>{existing ? 'Edit your review' : 'Rate this event'}</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{existing ? 'Edit your review' : 'How was it?'}</DialogTitle>
          <DialogDescription>Your first name shows next to your review.</DialogDescription>
        </DialogHeader>
        <fieldset className="flex gap-1">
          <legend className="sr-only">Rating</legend>
          {[1, 2, 3, 4, 5].map((n) => (
            <label key={n} className="cursor-pointer">
              <input
                type="radio"
                name="review-rating"
                value={n}
                checked={rating === n}
                onChange={() => setRating(n)}
                aria-label={`${n} ${n === 1 ? 'star' : 'stars'}`}
                className="peer sr-only"
              />
              <Star
                aria-hidden
                className={cn(
                  'size-8 rounded peer-focus-visible:ring-2 peer-focus-visible:ring-ring',
                  n <= rating ? 'fill-amber-400 text-amber-400' : 'text-muted-foreground/40',
                )}
              />
            </label>
          ))}
        </fieldset>
        <label className="block space-y-1.5 text-sm font-medium">
          Comment (optional)
          <textarea
            value={comment}
            onChange={(e) => setComment(e.target.value.slice(0, 1000))}
            className="mt-1.5 min-h-24 w-full rounded-xl border border-input bg-background px-3 py-2.5 text-sm font-normal outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
          />
          <span className="block text-right text-xs font-normal text-muted-foreground">{comment.length}/1000</span>
        </label>
        <DialogFooter className="gap-2 sm:justify-between">
          {existing ? (
            <Button variant="ghost" onClick={remove} disabled={busy}>
              Delete review
            </Button>
          ) : (
            <span />
          )}
          <Button onClick={submit} disabled={busy || rating < 1}>
            {save.isPending ? 'Saving…' : existing ? 'Save review' : 'Post review'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
