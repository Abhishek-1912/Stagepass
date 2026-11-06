import { ArrowLeft, Check, Star, X } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { ReasonDialog } from '@/components/feedback/ReasonDialog'
import { Button } from '@/components/ui/button'
import { StatusBadge } from '@/features/organizer/events/StatusBadge'
import { isApiError } from '@/lib/api/ApiError'
import { formatEventDate } from '@/lib/datetime'
import { formatPaise } from '@/lib/money'
import { useAdminEvent, useApproveEvent, useFeatureEvent, useRejectEvent } from './api'

export function EventReviewDetail() {
  const { id } = useParams()
  const { data: event, isPending } = useAdminEvent(id)
  const approve = useApproveEvent()
  const reject = useRejectEvent()
  const feature = useFeatureEvent()

  if (isPending || !event) return <PageLoader />

  async function run(action: () => Promise<unknown>, success: string) {
    try {
      await action()
      toast.success(success)
    } catch (e) {
      toast.error(isApiError(e) ? e.detail : 'Something went wrong')
      throw e
    }
  }

  return (
    <div className="space-y-6">
      <Link to="/admin/events" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" /> Event review
      </Link>

      <div className="grid gap-8 lg:grid-cols-[1fr_320px]">
        <article className="space-y-6">
          {event.coverImageUrl && <img src={event.coverImageUrl} alt="" className="aspect-[16/9] w-full rounded-3xl object-cover" />}
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <StatusBadge status={event.status} />
              <span className="text-sm text-muted-foreground">{event.categoryName}</span>
              {event.featured && <span className="text-sm font-semibold text-primary">Featured</span>}
            </div>
            <h2 className="text-3xl font-bold">{event.title}</h2>
            <p className="text-muted-foreground">
              by <span className="font-medium text-foreground">{event.organizerName}</span> · {formatEventDate(event.startsAt)}
            </p>
            <p className="text-muted-foreground">
              {event.online ? `Online · ${event.onlineUrl ?? ''}` : event.venue ? `${event.venue.name}, ${event.venue.addressLine}, ${event.venue.city}` : 'No venue'}
            </p>
          </div>
          {event.summary && <p className="text-lg">{event.summary}</p>}
          {event.description && <p className="whitespace-pre-line text-muted-foreground">{event.description}</p>}
          {event.gallery.length > 0 && (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              {event.gallery.map((g) => (
                <img key={g} src={g} alt="" className="aspect-square rounded-xl object-cover" />
              ))}
            </div>
          )}
          <section className="space-y-2">
            <h3 className="font-sans text-base font-semibold tracking-normal">Tickets</h3>
            <ul className="divide-y divide-border rounded-2xl border border-border bg-card">
              {event.ticketTypes.map((t) => (
                <li key={t.id} className="flex items-center justify-between px-4 py-3 text-sm">
                  <span>{t.name}</span>
                  <span className="text-muted-foreground">{t.quantity} available</span>
                  <span className="font-semibold">{formatPaise(t.pricePaise)}</span>
                </li>
              ))}
            </ul>
          </section>
          {event.terms && (
            <section className="space-y-2">
              <h3 className="font-sans text-base font-semibold tracking-normal">Terms</h3>
              <p className="whitespace-pre-line text-sm text-muted-foreground">{event.terms}</p>
            </section>
          )}
        </article>

        <aside className="space-y-3">
          {event.status === 'PENDING_REVIEW' && (
            <div className="space-y-3 rounded-2xl border border-border bg-card p-5">
              <h3 className="font-sans text-base font-semibold tracking-normal">Decision</h3>
              <Button className="w-full" disabled={approve.isPending} onClick={() => run(() => approve.mutateAsync(event.id), 'Event published').catch(() => {})}>
                <Check /> Approve and publish
              </Button>
              <ReasonDialog
                title="Request changes"
                description="The organizer is emailed this reason and can edit and resubmit."
                confirmLabel="Reject event"
                destructive
                trigger={
                  <Button variant="outline" className="w-full">
                    <X /> Reject
                  </Button>
                }
                onConfirm={(reason) => run(() => reject.mutateAsync({ id: event.id, reason }), 'Event rejected')}
              />
            </div>
          )}
          {event.status === 'PUBLISHED' && (
            <div className="space-y-3 rounded-2xl border border-border bg-card p-5">
              <h3 className="font-sans text-base font-semibold tracking-normal">Homepage</h3>
              <Button
                variant={event.featured ? 'outline' : 'default'}
                className="w-full"
                disabled={feature.isPending}
                onClick={() =>
                  run(() => feature.mutateAsync({ id: event.id, featured: !event.featured }), event.featured ? 'Removed from featured' : 'Featured').catch(() => {})
                }
              >
                <Star /> {event.featured ? 'Remove from featured' : 'Feature on homepage'}
              </Button>
            </div>
          )}
        </aside>
      </div>
    </div>
  )
}
