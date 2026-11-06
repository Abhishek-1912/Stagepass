import { MapPin, Pencil, Plus } from 'lucide-react'
import { useState } from 'react'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import type { Venue } from '@/lib/api/types'
import { useVenues } from '../api'
import { VenueForm } from './VenueForm'

export function VenuesPage() {
  const { data: venues, isPending } = useVenues()
  const [sheet, setSheet] = useState<{ open: boolean; venue?: Venue }>({ open: false })

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold">Venues</h2>
          <p className="text-muted-foreground">Places you host events. Reuse them across events.</p>
        </div>
        <Button className="rounded-full" onClick={() => setSheet({ open: true })}>
          <Plus /> Add venue
        </Button>
      </div>
      {isPending ? (
        <PageLoader />
      ) : venues?.length ? (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {venues.map((v) => (
            <li key={v.id} className="flex items-start gap-3 rounded-2xl border border-border bg-card p-5 shadow-[var(--shadow-card)]">
              <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-accent text-accent-foreground">
                <MapPin className="size-5" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="font-medium">{v.name}</p>
                <p className="truncate text-sm text-muted-foreground">
                  {v.addressLine}, {v.city}
                  {v.pincode ? ` ${v.pincode}` : ''}
                </p>
              </div>
              <Button variant="ghost" size="icon" aria-label={`Edit ${v.name}`} onClick={() => setSheet({ open: true, venue: v })}>
                <Pencil />
              </Button>
            </li>
          ))}
        </ul>
      ) : (
        <div className="rounded-2xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
          No venues yet.
        </div>
      )}
      <Sheet open={sheet.open} onOpenChange={(open) => setSheet((s) => ({ ...s, open }))}>
        <SheetContent className="w-full overflow-y-auto sm:max-w-md">
          <SheetHeader>
            <SheetTitle>{sheet.venue ? 'Edit venue' : 'Add venue'}</SheetTitle>
          </SheetHeader>
          <div className="px-4 pb-6">
            <VenueForm key={sheet.venue?.id ?? 'new'} venue={sheet.venue} onSaved={() => setSheet({ open: false })} />
          </div>
        </SheetContent>
      </Sheet>
    </div>
  )
}
