import { z } from 'zod'

const email = z.string().trim().email('Enter a valid email address')
const password = z.string().min(8, 'Use at least 8 characters')
const strongPassword = password
  .regex(/[A-Za-z]/, 'Include at least one letter')
  .regex(/\d/, 'Include at least one number')

export const loginSchema = z.object({ email, password })

export const registerSchema = z
  .object({
    fullName: z.string().trim().min(2, 'Enter your name').max(120, 'Keep it under 120 characters'),
    email,
    password: strongPassword,
    confirmPassword: z.string(),
  })
  .refine((v) => v.password === v.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords don’t match',
  })

export const forgotSchema = z.object({ email })

export const resetSchema = z
  .object({ password: strongPassword, confirmPassword: z.string() })
  .refine((v) => v.password === v.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords don’t match',
  })

export const otpCodeSchema = z.object({
  code: z
    .string()
    .trim()
    .regex(/^\d{6}$/, 'Enter the 6-digit code from your email'),
})

export type OtpCodeValues = z.infer<typeof otpCodeSchema>
export type LoginValues = z.infer<typeof loginSchema>
export type RegisterValues = z.infer<typeof registerSchema>
export type ForgotValues = z.infer<typeof forgotSchema>
export type ResetValues = z.infer<typeof resetSchema>
