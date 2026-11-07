import { ScanLine, UserPlus } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { isApiError } from '@/lib/api/ApiError'
import { useOrganizerEvents } from '../api'
import { type StaffMember, useInviteStaff, useRemoveStaff, useStaff } from './api'

/** `/organizer/staff` — the door team: people who can scan tickets for this organizer's events, and nothing else. */
export function StaffPage() {
  const staff = useStaff()
  const events = useOrganizerEvents()
  const invite = useInviteStaff()
  const remove = useRemoveStaff()
  const [email, setEmail] = useState('')
  const [eventId, setEventId] = useState('')
  const [removing, setRemoving] = useState<StaffMember | null>(null)

  const upcoming = (events.data?.items ?? []).filter((e) => e.status === 'PUBLISHED')

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!email.trim()) return
    try {
      await invite.mutateAsync({ email: email.trim(), eventId: eventId || null })
      toast.success(`Invite sent to ${email.trim()}`)
      setEmail('')
    } catch (err) {
      toast.error(isApiError(err) ? (err.fieldErrors[0]?.message ?? err.detail) : 'Could not send the invite')
    }
  }

  async function confirmRemove() {
    if (!removing) return
    try {
      await remove.mutateAsync(removing.id)
      toast.success(`${removing.name ?? removing.email} can no longer scan`)
      setRemoving(null)
    } catch (err) {
      toast.error(isApiError(err) ? err.detail : 'Could not remove')
    }
  }

  const active = (staff.data ?? []).filter((s) => s.status === 'ACTIVE')

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h2 className="text-2xl font-bold">Door staff</h2>
        <p className="text-muted-foreground">
          People you add here can open the Stagepass scanner and check guests in — nothing else. They never see sales, payouts or
          attendee lists.
        </p>
      </div>

      <form onSubmit={submit} className="space-y-4 rounded-3xl border border-border bg-card p-6">
        <div className="grid gap-4 sm:grid-cols-[1fr_220px]">
          <div className="space-y-1.5">
            <Label htmlFor="staff-email">Email</Label>
            <Input id="staff-email" type="email" placeholder="door@yourteam.in" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="staff-event">Events</Label>
            <select
              id="staff-event"
              value={eventId}
              onChange={(e) => setEventId(e.target.value)}
              className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
            >
              <option value="">All my events</option>
              {upcoming.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.title}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-xs text-muted-foreground">They get an email and sign in with that address — no password needed.</p>
          <Button type="submit" className="rounded-full" disabled={invite.isPending || !email.trim()}>
            <UserPlus className="size-4" aria-hidden /> Send invite
          </Button>
        </div>
      </form>

      {staff.isPending ? (
        <PageLoader label="Loading door staff" />
      ) : active.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          No door staff yet. You can always scan your own events from the scanner.
        </p>
      ) : (
        <ul className="divide-y divide-border rounded-2xl border border-border">
          {active.map((s) => (
            <li key={s.id} className="flex flex-wrap items-center justify-between gap-3 p-4 text-sm">
              <div className="min-w-0">
                <p className="font-medium">{s.name ?? s.email}</p>
                {s.name && <p className="text-muted-foreground">{s.email}</p>}
                <p className="flex items-center gap-1 text-xs text-muted-foreground">
                  <ScanLine className="size-3.5" aria-hidden /> {s.eventTitle ? `Can scan ${s.eventTitle}` : 'Can scan all events'}
                </p>
              </div>
              <div className="flex items-center gap-3">
                <span className={s.acceptedAt ? 'text-xs font-medium text-emerald-700 dark:text-emerald-300' : 'text-xs text-muted-foreground'}>
                  {s.acceptedAt ? 'Active' : 'Invited — not signed in yet'}
                </span>
                <Button variant="outline" size="sm" className="rounded-full" onClick={() => setRemoving(s)}>
                  Remove
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Dialog open={!!removing} onOpenChange={(open) => !open && setRemoving(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Remove from the door team?</DialogTitle>
            <DialogDescription>
              {removing && `${removing.name ?? removing.email} won't be able to scan tickets from their next scan onwards.`}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRemoving(null)}>
              Keep
            </Button>
            <Button variant="destructive" onClick={confirmRemove} disabled={remove.isPending}>
              Remove
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
