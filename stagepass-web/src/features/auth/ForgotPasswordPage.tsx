import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { supabase } from '@/lib/supabase'
import { AuthCard } from './AuthCard'
import { CheckEmail } from './CheckEmail'
import { FormField } from './FormField'
import { type ForgotValues, forgotSchema } from './schemas'

export function ForgotPasswordPage() {
  const [sentTo, setSentTo] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotValues>({ resolver: zodResolver(forgotSchema) })

  async function onSubmit({ email }: ForgotValues) {
    // Always show the same confirmation, whether or not the email exists, so accounts can't be probed.
    await supabase.auth.resetPasswordForEmail(email, { redirectTo: `${window.location.origin}/reset-password` })
    setSentTo(email)
  }

  return (
    <AuthCard
      title="Reset your password"
      subtitle="Enter your account email and we’ll send you a reset link."
      footer={
        <Link to="/login" className="font-medium text-primary hover:underline">
          Back to sign in
        </Link>
      }
    >
      {sentTo ? (
        <CheckEmail email={sentTo}>If an account exists for it, the link will arrive in a minute.</CheckEmail>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <FormField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
          <Button type="submit" size="lg" className="h-11 w-full rounded-xl" disabled={isSubmitting}>
            {isSubmitting ? 'Sending…' : 'Send reset link'}
          </Button>
        </form>
      )}
    </AuthCard>
  )
}
