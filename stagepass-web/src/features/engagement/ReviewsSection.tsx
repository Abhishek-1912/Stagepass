import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { ConfirmAction } from '@/features/admin/ConfirmAction'
import { isApiError } from '@/lib/api/ApiError'
import { ReviewDialog } from './ReviewDialog'
import { useAdminRemoveReview, useEventReviews, useMyReview } from './reviewsApi'
import { Stars } from './Stars'
import { useViewer } from './useViewer'

const dateFmt = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeZone: 'Asia/Kolkata' })

export interface ReviewedEvent {
  id: string
  slug: string
  endsAt: string
  status: string
  rating: { average: number | null; count: number }
}

/** Star summary, newest reviews, and (for attendees of a finished event) a way to rate it. */
export function ReviewsSection({ event }: { event: ReviewedEvent }) {
  const viewer = useViewer()
  const reviews = useEventReviews(event.slug)
  const mine = useMyReview(event.id, viewer.signedIn)
  const remove = useAdminRemoveReview()
  const isAdmin = viewer.roles.includes('ADMIN')
  const items = reviews.data?.pages.flatMap((p) => p.items) ?? []
  const { average, count } = event.rating

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <h2 className="text-xl font-bold">Reviews</h2>
          {count > 0 && average != null && (
            <span className="flex items-center gap-2 text-sm">
              <span className="font-semibold">{average.toFixed(1)}</span>
              <Stars value={average} />
              <span className="text-muted-foreground">{count === 1 ? '1 review' : `${count} reviews`}</span>
            </span>
          )}
        </div>
        {mine.data?.eligible && <ReviewDialog eventId={event.id} existing={mine.data.review} />}
      </div>
      {items.length === 0 ? (
        <p className="text-sm text-muted-foreground">No reviews yet.</p>
      ) : (
        <div className="space-y-3">
          {items.map((r) => (
            <article key={r.id} className="space-y-1.5 rounded-2xl border border-border bg-card p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="flex items-center gap-2">
                  <Stars value={r.rating} />
                  <span className="text-sm font-medium">{r.authorName}</span>
                  <span className="text-xs text-muted-foreground">{dateFmt.format(new Date(r.createdAt))}</span>
                </span>
                {isAdmin && (
                  <ConfirmAction
                    trigger={<Button size="sm" variant="ghost">Remove</Button>}
                    title="Remove this review?"
                    effect="It is deleted for good and the event's rating is recalculated. Use this for abuse or spam."
                    confirmLabel="Remove review"
                    destructive
                    onConfirm={async () => {
                      try {
                        await remove.mutateAsync(r.id)
                        toast.success('Review removed')
                      } catch (e) {
                        toast.error(isApiError(e) ? e.detail : 'Could not remove the review')
                        throw e
                      }
                    }}
                  />
                )}
              </div>
              {r.comment && <p className="whitespace-pre-line text-sm">{r.comment}</p>}
            </article>
          ))}
          {reviews.hasNextPage && (
            <Button variant="outline" onClick={() => reviews.fetchNextPage()} disabled={reviews.isFetchingNextPage}>
              {reviews.isFetchingNextPage ? 'Loading…' : 'Show more'}
            </Button>
          )}
        </div>
      )}
    </section>
  )
}
