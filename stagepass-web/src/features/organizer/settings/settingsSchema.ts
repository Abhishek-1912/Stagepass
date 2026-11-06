import { z } from 'zod'

const optional = (schema: z.ZodString) => z.union([z.literal(''), schema])

/** Mirrors the server rules for PATCH /organizer/profile. */
export const settingsSchema = z
  .object({
    bio: z.string().max(1000, 'Keep it under 1000 characters'),
    logoUrl: z.string(),
    website: optional(z.string().regex(/^https:\/\/[^\s/]+\.[^\s]+$/, 'Enter a full https:// address')),
    contactEmail: optional(z.string().email('Enter a valid email')),
    contactPhone: optional(z.string().regex(/^[6-9]\d{9}$/, 'Enter a 10-digit Indian mobile number')),
    payoutAccountName: z.string().max(120),
    changeAccount: z.boolean(),
    payoutAccountNumber: z.string(),
    payoutIfsc: z.string(),
  })
  .superRefine((v, ctx) => {
    if (!v.changeAccount) return
    if (!/^\d{9,18}$/.test(v.payoutAccountNumber.replace(/\s+/g, ''))) {
      ctx.addIssue({ code: 'custom', path: ['payoutAccountNumber'], message: 'Enter 9–18 digits' })
    }
    if (!/^[A-Z]{4}0[A-Z0-9]{6}$/.test(v.payoutIfsc.trim().toUpperCase())) {
      ctx.addIssue({ code: 'custom', path: ['payoutIfsc'], message: 'Enter a valid IFSC, e.g. HDFC0001234' })
    }
  })

export type SettingsForm = z.infer<typeof settingsSchema>
