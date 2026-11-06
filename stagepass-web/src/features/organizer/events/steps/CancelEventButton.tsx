import { toast } from 'sonner'
import { ReasonDialog } from '@/components/feedback/ReasonDialog'
import { Button } from '@/components/ui/button'
import { isApiError } from '@/lib/api/ApiError'
import type { EventDetail } from '@/lib/api/types'
import { useCancelEvent } from '../../api'

export function CancelEventButton({ event }: { event: EventDetail }) {
  const cancel = useCancelEvent(event.id)
  return (
    <ReasonDialog
      destructive
      title="Cancel this event?"
      description="Ticket holders will be refunded in full and emailed. This can’t be undone."
      confirmLabel="Cancel event"
      placeholder="Tell attendees why the event is cancelled"
      trigger={
        <Button variant="ghost" className="w-full text-destructive hover:text-destructive">
          Cancel event
        </Button>
      }
      onConfirm={async (reason) => {
        try {
          await cancel.mutateAsync(reason)
          toast.success('Event cancelled')
        } catch (e) {
          toast.error(isApiError(e) ? e.detail : 'Could not cancel the event')
          throw e
        }
      }}
    />
  )
}
