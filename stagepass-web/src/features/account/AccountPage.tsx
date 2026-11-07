import { Heart, Ticket } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useMe, useUpdateMe, type Me, type ProfileUpdate } from '@/features/auth/useMe'
import { useMyBookings } from '@/features/bookings/api'
import { isApiError } from '@/lib/api/ApiError'
import { formatEventDate } from '@/lib/datetime'
import { cn } from '@/lib/utils'
import { AvatarUpload } from './AvatarUpload'

function ProfileForm({ me }: { me: Me }) {
  const update = useUpdateMe()
  const [fullName, setFullName] = useState(me.fullName ?? '')
  const [phone, setPhone] = useState(me.phone ?? '')
  const [city, setCity] = useState(me.city ?? '')
  const [errors, setErrors] = useState<Record<string, string>>({})

  async function submit(e: FormEvent) {
    e.preventDefault()
    const changes: ProfileUpdate = {}
    if (fullName.trim() && fullName.trim() !== (me.fullName ?? '')) changes.fullName = fullName.trim()
    if (phone.trim() && phone.trim() !== (me.phone ?? '')) changes.phone = phone.trim()
    if (city.trim() !== (me.city ?? '')) changes.city = city.trim()
    if (Object.keys(changes).length === 0) {
      toast.success('Nothing to save')
      return
    }
    setErrors({})
    try {
      await update.mutateAsync(changes)
      toast.success('Profile saved')
    } catch (err) {
      if (isApiError(err) && err.fieldErrors.length) {
        setErrors(Object.fromEntries(err.fieldErrors.map((f) => [f.field, f.message])))
      } else {
        toast.error(isApiError(err) ? err.detail : 'Could not save your profile')
      }
    }
  }

  const field = (id: string, label: string, value: string, set: (v: string) => void, extra?: { inputMode?: 'tel'; placeholder?: string }) => (
    <div className="space-y-1.5">
      <Label htmlFor={`profile-${id}`}>{label}</Label>
      <Input id={`profile-${id}`} value={value} onChange={(e) => set(e.target.value)} aria-invalid={!!errors[id]} {...extra} />
      {errors[id] && <p className="text-xs text-destructive">{errors[id]}</p>}
    </div>
  )

  return (
    <form onSubmit={submit} className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-3">
        {field('fullName', 'Name', fullName, setFullName)}
        {field('phone', 'Phone', phone, setPhone, { inputMode: 'tel', placeholder: '98765 43210' })}
        {field('city', 'City', city, setCity)}
      </div>
      <Button type="submit" disabled={update.isPending}>
        {update.isPending ? 'Saving…' : 'Save profile'}
      </Button>
    </form>
  )
}

function ReminderSwitch({ me }: { me: Me }) {
  const update = useUpdateMe()
  const [on, setOn] = useState(me.emailReminders)
  async function toggle() {
    const next = !on
    setOn(next)
    try {
      await update.mutateAsync({ emailReminders: next })
    } catch {
      setOn(!next)
      toast.error('Could not save the setting')
    }
  }
  return (
    <div className="flex items-start justify-between gap-4">
      <div>
        <p id="reminder-label" className="font-medium">Email me a reminder 24 hours before my events</p>
        <p className="text-sm text-muted-foreground">You'll always see reminders in the bell, whatever you choose here.</p>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={on}
        aria-labelledby="reminder-label"
        onClick={toggle}
        className={cn('relative h-6 w-11 shrink-0 rounded-full transition-colors', on ? 'bg-primary' : 'bg-muted')}
      >
        <span className={cn('absolute top-0.5 size-5 rounded-full bg-white shadow transition-transform', on ? 'translate-x-5.5' : 'translate-x-0.5')} />
      </button>
    </div>
  )
}

function UpcomingTickets() {
  const { data, isPending } = useMyBookings('upcoming')
  const items = data?.items.slice(0, 3) ?? []
  return (
    <section className="space-y-3 rounded-2xl border border-border bg-card p-5">
      <div className="flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-lg font-semibold"><Ticket className="size-5" aria-hidden /> Upcoming tickets</h2>
        <Link to="/account/bookings" className="text-sm font-medium text-primary hover:underline">All tickets</Link>
      </div>
      {isPending ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          No upcoming events. <Link to="/explore" className="text-primary hover:underline">Find something to do</Link>
        </p>
      ) : (
        <ul className="divide-y divide-border">
          {items.map((b) => (
            <li key={b.id}>
              <Link to={`/account/bookings/${b.id}`} className="flex items-center justify-between gap-3 py-3 hover:text-primary">
                <span className="min-w-0">
                  <span className="block truncate font-medium">{b.eventTitle}</span>
                  <span className="block text-sm text-muted-foreground">{formatEventDate(b.eventStartsAt)}{b.city ? ` · ${b.city}` : ''}</span>
                </span>
                <span className="shrink-0 text-sm text-muted-foreground">{b.ticketCount} {b.ticketCount === 1 ? 'ticket' : 'tickets'}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/** The attendee's home: profile, upcoming tickets, saved events and the reminder setting. */
export function AccountPage() {
  const { data: me, isPending, error } = useMe()
  if (isPending) return <PageLoader />
  if (error || !me) {
    return (
      <div className="mx-auto max-w-xl px-4 py-16">
        <p role="alert" className="rounded-xl bg-destructive/10 p-4 text-destructive">
          {isApiError(error) ? error.detail : 'We couldn’t load your account.'}
        </p>
      </div>
    )
  }
  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-10 sm:px-6">
      <div className="space-y-1">
        <h1 className="text-3xl font-bold">Hi {me.fullName?.split(' ')[0] ?? 'there'}</h1>
        <p className="text-muted-foreground">{me.email}</p>
      </div>
      <UpcomingTickets />
      <Link
        to="/account/saved"
        className="flex items-center justify-between rounded-2xl border border-border bg-card p-5 font-medium hover:border-primary/40"
      >
        <span className="flex items-center gap-2"><Heart className="size-5" aria-hidden /> Saved events</span>
        <span aria-hidden>→</span>
      </Link>
      <section className="space-y-5 rounded-2xl border border-border bg-card p-5">
        <h2 className="text-lg font-semibold">Profile</h2>
        <AvatarUpload me={me} />
        <ProfileForm me={me} />
      </section>
      <section className="rounded-2xl border border-border bg-card p-5">
        <h2 className="mb-4 text-lg font-semibold">Notifications</h2>
        <ReminderSwitch me={me} />
      </section>
    </div>
  )
}
