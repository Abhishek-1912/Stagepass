import { z } from 'zod'

const optional = (schema: z.ZodString) =>
  z
    .string()
    .trim()
    .transform((v) => (v === '' ? undefined : v))
    .pipe(schema.optional())

const upper = (regex: RegExp, message: string) =>
  z
    .string()
    .trim()
    .transform((v) => v.toUpperCase())
    .pipe(z.string().regex(regex, message))

/** Same rules as the API's OrganizerApplicationRequest. */
export const applicationSchema = z.object({
  orgName: z.string().trim().min(2, 'Enter your organization name').max(120),
  bio: optional(z.string().max(2000)),
  logoUrl: z.string().optional(),
  contactEmail: z.string().trim().email('Enter a valid email address'),
  contactPhone: optional(z.string().regex(/^[6-9]\d{9}$/, 'Enter a 10-digit Indian mobile number')),
  website: optional(z.string().url('Enter a full URL, e.g. https://example.com')),
  pan: upper(/^[A-Z]{5}[0-9]{4}[A-Z]$/, 'Enter a valid PAN, e.g. ABCDE1234F'),
  gstin: z
    .string()
    .trim()
    .transform((v) => (v === '' ? undefined : v.toUpperCase()))
    .pipe(
      z
        .string()
        .regex(/^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/, 'Enter a valid 15-character GSTIN')
        .optional(),
    ),
  state: z.string().trim().min(2, 'Choose your state'),
  payoutAccountName: z.string().trim().min(2, 'Enter the account holder name'),
  payoutAccountNumber: z
    .string()
    .transform((v) => v.replace(/\s+/g, ''))
    .pipe(z.string().regex(/^[0-9]{9,18}$/, 'Account numbers are 9–18 digits')),
  payoutIfsc: upper(/^[A-Z]{4}0[A-Z0-9]{6}$/, 'Enter a valid IFSC, e.g. HDFC0001234'),
})

export type ApplicationFormInput = z.input<typeof applicationSchema>
export type ApplicationFormValues = z.output<typeof applicationSchema>

export const INDIAN_STATES = [
  'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chhattisgarh', 'Goa', 'Gujarat', 'Haryana',
  'Himachal Pradesh', 'Jharkhand', 'Karnataka', 'Kerala', 'Madhya Pradesh', 'Maharashtra', 'Manipur', 'Meghalaya',
  'Mizoram', 'Nagaland', 'Odisha', 'Punjab', 'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura',
  'Uttar Pradesh', 'Uttarakhand', 'West Bengal', 'Andaman and Nicobar Islands', 'Chandigarh',
  'Dadra and Nagar Haveli and Daman and Diu', 'Delhi', 'Jammu and Kashmir', 'Ladakh', 'Lakshadweep', 'Puducherry',
]
