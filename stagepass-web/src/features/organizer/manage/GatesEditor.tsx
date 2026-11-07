import { Trash2 } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { type Gate } from '@/features/checkin/types'
import { useCreateGate, useDeleteGate, useGates } from '@/features/checkin/api'
import { isApiError } from '@/lib/api/ApiError'

interface TicketTypeOption {
  id: string
  name: string
}

/** Named entry gates for an event; each can be limited to some ticket types (none ticked = all types). */
export function GatesEditor({ eventId, ticketTypes }: { eventId: string; ticketTypes: TicketTypeOption[] }) {
  const gates = useGates(eventId)
  const create = useCreateGate(eventId)
  const del = useDeleteGate(eventId)
  const [name, setName] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [deleting, setDeleting] = useState<Gate | null>(null)

  const typeName = (id: string) => ticketTypes.find((t) => t.id === id)?.name ?? 'Unknown type'

  async function add(e: FormEvent) {
    e.preventDefault()
    if (!name.trim()) return
    try {
      await create.mutateAsync({ name: name.trim(), ticketTypeIds: selected })
      setName('')
      setSelected([])
    } catch (err) {
      toast.error(isApiError(err) ? (err.fieldErrors[0]?.message ?? err.detail) : 'Could not add the gate')
    }
  }

  async function confirmDelete() {
    if (!deleting) return
    try {
      await del.mutateAsync(deleting.id)
      setDeleting(null)
    } catch (err) {
      toast.error(isApiError(err) ? err.detail : 'Could not delete the gate')
    }
  }

  return (
    <section className="space-y-4 rounded-3xl border border-border bg-card p-6">
      <div>
        <h3 className="font-sans text-lg font-semibold tracking-normal">Gates</h3>
        <p className="text-sm text-muted-foreground">Optional. Door staff pick their gate in the scanner; a ticket at a gate it isn’t allowed through is turned away.</p>
      </div>

      {(gates.data ?? []).length > 0 && (
        <ul className="divide-y divide-border rounded-2xl border border-border text-sm">
          {gates.data!.map((g) => (
            <li key={g.id} className="flex flex-wrap items-center justify-between gap-2 p-3">
              <div>
                <p className="font-medium">{g.name}</p>
                <p className="text-xs text-muted-foreground">
                  {g.ticketTypeIds.length === 0 ? 'All ticket types' : g.ticketTypeIds.map(typeName).join(', ')} · {g.checkedIn} checked in
                </p>
              </div>
              <Button variant="ghost" size="icon" aria-label={`Delete ${g.name}`} onClick={() => setDeleting(g)}>
                <Trash2 className="size-4" />
              </Button>
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={add} className="space-y-3">
        <div className="space-y-1.5">
          <Label htmlFor="gate-name">Gate name</Label>
          <Input id="gate-name" maxLength={40} placeholder="e.g. VIP Gate" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        {ticketTypes.length > 1 && (
          <fieldset className="space-y-1.5">
            <legend className="text-sm font-medium">Only these ticket types (leave empty for all)</legend>
            <div className="flex flex-wrap gap-3">
              {ticketTypes.map((t) => (
                <label key={t.id} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="size-4 accent-primary"
                    checked={selected.includes(t.id)}
                    onChange={() => setSelected((s) => (s.includes(t.id) ? s.filter((x) => x !== t.id) : [...s, t.id]))}
                  />
                  {t.name}
                </label>
              ))}
            </div>
          </fieldset>
        )}
        <Button type="submit" variant="outline" className="rounded-full" disabled={create.isPending || !name.trim()}>
          Add gate
        </Button>
      </form>

      <Dialog open={!!deleting} onOpenChange={(open) => !open && setDeleting(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete {deleting?.name}?</DialogTitle>
            <DialogDescription>Check-in history is kept; past scans will just show no gate.</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleting(null)}>
              Keep
            </Button>
            <Button variant="destructive" onClick={confirmDelete} disabled={del.isPending}>
              Delete gate
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
