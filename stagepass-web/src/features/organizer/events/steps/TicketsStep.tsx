import { zodResolver } from '@hookform/resolvers/zod'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { SelectField, TextAreaField, TextField } from '@/components/form/fields'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { isApiError } from '@/lib/api/ApiError'
import type { EventDetail, RefundPolicy, TicketType, TicketTypeInput } from '@/lib/api/types'
import { istInputToIso, isoToIstInput } from '@/lib/datetime'
import { formatPaise, paiseToRupeesInput } from '@/lib/money'
import { useDeleteTicketType, useSaveTicketType, useUpdateEvent } from '../../api'
import { type TicketTypeFormInput, type TicketTypeFormValues, ticketTypeFormSchema } from '../eventSchema'

function TicketTypeDialog({
  event,
  editing,
  open,
  onOpenChange,
}: {
  event: EventDetail
  editing: TicketType | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const save = useSaveTicketType(event.id)
  const priceLocked = (editing?.sold ?? 0) > 0
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<TicketTypeFormInput, unknown, TicketTypeFormValues>({
    resolver: zodResolver(ticketTypeFormSchema),
    values: {
      name: editing?.name ?? '',
      price: editing ? paiseToRupeesInput(editing.pricePaise) : '',
      quantity: editing ? String(editing.quantity) : '',
      minPerOrder: String(editing?.minPerOrder ?? 1),
      maxPerOrder: String(editing?.maxPerOrder ?? 10),
      saleStartsAt: editing?.saleStartsAt ? isoToIstInput(editing.saleStartsAt) : '',
      saleEndsAt: editing?.saleEndsAt ? isoToIstInput(editing.saleEndsAt) : '',
      description: editing?.description ?? '',
    },
  })

  async function onSubmit(v: TicketTypeFormValues) {
    const input: TicketTypeInput = {
      name: v.name,
      quantity: v.quantity,
      minPerOrder: v.minPerOrder,
      maxPerOrder: v.maxPerOrder,
      description: v.description || undefined,
      saleStartsAt: v.saleStartsAt ? istInputToIso(v.saleStartsAt) : undefined,
      saleEndsAt: v.saleEndsAt ? istInputToIso(v.saleEndsAt) : undefined,
      ...(priceLocked ? {} : { pricePaise: v.pricePaise }),
    }
    try {
      await save.mutateAsync({ id: editing?.id, input })
      toast.success(editing ? 'Ticket type updated' : 'Ticket type added')
      onOpenChange(false)
    } catch (e) {
      toast.error(isApiError(e) ? (e.fieldErrors[0]?.message ?? e.detail) : 'Could not save the ticket type')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{editing ? `Edit ${editing.name}` : 'Add ticket type'}</DialogTitle>
          <DialogDescription>Prices are in rupees. Attendees pay a small convenience fee on top.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <TextField label="Name" placeholder="General admission" error={errors.name?.message} {...register('name')} />
          </div>
          <TextField
            label="Price (₹)"
            inputMode="decimal"
            placeholder="0 for free"
            disabled={priceLocked}
            hint={priceLocked ? 'Price is locked after the first sale. Add a new type to change it.' : undefined}
            error={errors.price?.message}
            {...register('price')}
          />
          <TextField
            label="Quantity"
            inputMode="numeric"
            hint={editing && editing.sold + editing.held > 0 ? `${editing.sold + editing.held} sold or reserved` : undefined}
            error={errors.quantity?.message}
            {...register('quantity')}
          />
          <TextField label="Min per order" inputMode="numeric" error={errors.minPerOrder?.message} {...register('minPerOrder')} />
          <TextField label="Max per order" inputMode="numeric" error={errors.maxPerOrder?.message} {...register('maxPerOrder')} />
          <TextField label="Sales start (IST)" type="datetime-local" error={errors.saleStartsAt?.message} {...register('saleStartsAt')} />
          <TextField label="Sales end (IST)" type="datetime-local" error={errors.saleEndsAt?.message} {...register('saleEndsAt')} />
          <div className="sm:col-span-2">
            <TextAreaField label="Description (optional)" className="min-h-20" {...register('description')} />
          </div>
          <DialogFooter className="sm:col-span-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Saving…' : 'Save ticket type'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function BookingRules({ event }: { event: EventDetail }) {
  const update = useUpdateEvent(event.id)
  const [maxPerUser, setMaxPerUser] = useState(event.maxTicketsPerUser?.toString() ?? '')
  const [policy, setPolicy] = useState<RefundPolicy>(event.refundPolicy)
  const [cutoff, setCutoff] = useState(event.refundCutoffHours?.toString() ?? '48')

  async function saveRules() {
    try {
      await update.mutateAsync({
        maxTicketsPerUser: maxPerUser ? Number(maxPerUser) : undefined,
        refundPolicy: policy,
        refundCutoffHours: policy === 'UNTIL_HOURS_BEFORE' ? Number(cutoff) : undefined,
      })
      toast.success('Booking rules saved')
    } catch (e) {
      toast.error(isApiError(e) ? (e.fieldErrors[0]?.message ?? e.detail) : 'Could not save booking rules')
    }
  }

  return (
    <section className="space-y-4 rounded-2xl border border-border bg-card p-5">
      <h3 className="font-sans text-base font-semibold tracking-normal">Booking rules</h3>
      <div className="grid gap-4 sm:grid-cols-3">
        <TextField label="Max tickets per person" inputMode="numeric" placeholder="No limit" value={maxPerUser} onChange={(e) => setMaxPerUser(e.target.value)} />
        <SelectField label="Refunds" value={policy} onChange={(e) => setPolicy(e.target.value as RefundPolicy)}>
          <option value="NONE">No attendee refunds</option>
          <option value="UNTIL_HOURS_BEFORE">Allowed until…</option>
        </SelectField>
        {policy === 'UNTIL_HOURS_BEFORE' && (
          <TextField label="Hours before start" inputMode="numeric" value={cutoff} onChange={(e) => setCutoff(e.target.value)} />
        )}
      </div>
      <p className="text-xs text-muted-foreground">
        If you cancel or reschedule the event, ticket holders can always get a full refund.
      </p>
      <Button variant="outline" onClick={saveRules} disabled={update.isPending}>
        Save rules
      </Button>
    </section>
  )
}

export function TicketsStep({ event }: { event: EventDetail }) {
  const remove = useDeleteTicketType(event.id)
  const [dialog, setDialog] = useState<{ open: boolean; editing: TicketType | null }>({ open: false, editing: null })

  async function deleteType(t: TicketType) {
    try {
      await remove.mutateAsync(t.id)
      toast.success(`${t.name} removed`)
    } catch (e) {
      toast.error(isApiError(e) ? e.detail : 'Could not remove the ticket type')
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold">Tickets</h2>
          <p className="text-sm text-muted-foreground">Add one or more ticket types, like General and VIP.</p>
        </div>
        <Button className="rounded-full" onClick={() => setDialog({ open: true, editing: null })}>
          <Plus /> Add ticket type
        </Button>
      </div>

      {event.ticketTypes.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
          No ticket types yet. Add at least one to publish your event.
        </div>
      ) : (
        <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card">
          {event.ticketTypes.map((t) => (
            <li key={t.id} className="flex flex-wrap items-center gap-4 px-5 py-4">
              <div className="min-w-40 flex-1">
                <p className="font-medium">{t.name}</p>
                <p className="text-xs text-muted-foreground">
                  {t.minPerOrder}–{t.maxPerOrder} per order{t.status !== 'ACTIVE' ? ` · ${t.status.toLowerCase()}` : ''}
                </p>
              </div>
              <p className="w-24 font-semibold">{formatPaise(t.pricePaise)}</p>
              <p className="w-32 text-sm text-muted-foreground">
                {t.sold} sold · {t.available} left
              </p>
              <div className="flex gap-1">
                <Button variant="ghost" size="icon" aria-label={`Edit ${t.name}`} onClick={() => setDialog({ open: true, editing: t })}>
                  <Pencil />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={`Remove ${t.name}`}
                  disabled={t.sold + t.held > 0}
                  title={t.sold + t.held > 0 ? 'Tickets of this type were sold' : undefined}
                  onClick={() => deleteType(t)}
                >
                  <Trash2 />
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <BookingRules event={event} />

      <TicketTypeDialog
        event={event}
        editing={dialog.editing}
        open={dialog.open}
        onOpenChange={(open) => setDialog((d) => ({ ...d, open }))}
      />
    </div>
  )
}
