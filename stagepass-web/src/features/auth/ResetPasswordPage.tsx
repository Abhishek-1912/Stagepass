import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { supabase } from '@/lib/supabase'
import { AuthCard } from './AuthCard'
import { useAuth } from './AuthProvider'
import { FormField } from './FormField'
import { type ResetValues, resetSchema } from './schemas'

/** Reached from the Supabase recovery email; Supabase signs the user in with a recovery session first. */
export function ResetPasswordPage() {
  const { session, loading, recovering } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResetValues>({ resolver: zodResolver(resetSchema) })

  async function onSubmit({ password }: ResetValues) {
    setFormError(null)
    const { error } = await supabase.auth.updateUser({ password })
    if (error) {
      setFormError(error.message || 'We couldn’t update your password. Request a new link and try again.')
      return
    }
    toast.success('Password updated. You’re signed in.')
    navigate('/', { replace: true })
  }

  if (loading) return <PageLoader />

  return (
    <AuthCard title="Choose a new password" subtitle="Make it something you haven’t used before.">
      {!session && !recovering ? (
        <div className="space-y-4 text-center">
          <p className="text-muted-foreground">This reset link has expired or was already used.</p>
          <Button asChild className="rounded-xl">
            <Link to="/forgot-password">Request a new link</Link>
          </Button>
        </div>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <FormField
            label="New password"
            type="password"
            autoComplete="new-password"
            hint="At least 8 characters, with a letter and a number."
            error={errors.password?.message}
            {...register('password')}
          />
          <FormField
            label="Confirm new password"
            type="password"
            autoComplete="new-password"
            error={errors.confirmPassword?.message}
            {...register('confirmPassword')}
          />
          {formError && (
            <p role="alert" className="rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {formError}
            </p>
          )}
          <Button type="submit" size="lg" className="h-11 w-full rounded-xl" disabled={isSubmitting}>
            {isSubmitting ? 'Saving…' : 'Update password'}
          </Button>
        </form>
      )}
    </AuthCard>
  )
}
