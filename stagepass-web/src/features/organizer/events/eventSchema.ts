import { z } from 'zod'
import { rupeesToPaise } from '@/lib/money'

export const basicsSchema = z.object({
  title: z.string().trim().min(3, 'Give your event a title (3+ characters)').max(140),
  categoryId: z.string().min(1, 'Choose a category'),
  summary: z.string().trim().max(280, 'Keep the summary under 280 characters').optional(),
  description: z.string().max(20000).optional(),
  terms: z.string().max(10000).optional(),
})
export type BasicsValues = z.infer<typeof basicsSchema>

const datetime = z.string().regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/, 'Pick a date and time')

export const scheduleSchema = z
  .object({ startsAt: datetime, endsAt: datetime })
  .refine((v) => v.endsAt > v.startsAt, { path: ['endsAt'], message: 'End time must be after the start time' })

export const whenWhereSchema = z
  .object({
    startsAt: datetime,
    endsAt: datetime,
    online: z.boolean(),
    venueId: z.string().optional(),
    onlineUrl: z.string().trim().optional(),
  })
  .superRefine((v, ctx) => {
    if (v.endsAt <= v.startsAt) {
      ctx.addIssue({ code: 'custom', path: ['endsAt'], message: 'End time must be after the start time' })
    }
    if (v.online && !/^https?:\/\/\S+$/.test(v.onlineUrl ?? '')) {
      ctx.addIssue({ code: 'custom', path: ['onlineUrl'], message: 'Add the link attendees will join' })
    }
    if (!v.online && !v.venueId) {
      ctx.addIssue({ code: 'custom', path: ['venueId'], message: 'Choose a venue' })
    }
  })
export type WhenWhereValues = z.infer<typeof whenWhereSchema>

const wholeNumber = (min: number, message: string) =>
  z
    .string()
    .trim()
    .regex(/^\d+$/, message)
    .transform(Number)
    .pipe(z.number().int().min(min, message))

export const ticketTypeFormSchema = z
  .object({
    name: z.string().trim().min(1, 'Name the ticket type').max(80),
    price: z.string().transform((value, ctx) => {
      try {
        return rupeesToPaise(value)
      } catch (e) {
        ctx.addIssue({ code: 'custom', message: (e as Error).message })
        return z.NEVER
      }
    }),
    quantity: wholeNumber(1, 'Enter how many tickets are available'),
    minPerOrder: wholeNumber(1, 'At least 1'),
    maxPerOrder: wholeNumber(1, 'At least 1'),
    saleStartsAt: z.string().optional(),
    saleEndsAt: z.string().optional(),
    description: z.string().max(500).optional(),
  })
  .superRefine((v, ctx) => {
    if (v.maxPerOrder < v.minPerOrder) {
      ctx.addIssue({ code: 'custom', path: ['maxPerOrder'], message: 'Must be at least the minimum' })
    }
    if (v.saleStartsAt && v.saleEndsAt && v.saleEndsAt <= v.saleStartsAt) {
      ctx.addIssue({ code: 'custom', path: ['saleEndsAt'], message: 'Sales must end after they start' })
    }
  })
  .transform(({ price, ...rest }) => ({ ...rest, pricePaise: price }))

export type TicketTypeFormInput = z.input<typeof ticketTypeFormSchema>
export type TicketTypeFormValues = z.output<typeof ticketTypeFormSchema>
