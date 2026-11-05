import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useSearchParams } from 'react-router'
import { Button } from '@/components/ui/button'
import { supabase } from '@/lib/supabase'
import { AuthCard } from './AuthCard'
import { useAuth } from './AuthProvider'
import { CheckEmail } from './CheckEmail'
import { FormField } from './FormField'
import { GoogleButton, OrDivider } from './GoogleButton'
import { safeNext } from './safeNext'
import { type RegisterValues, registerSchema } from './schemas'

export function RegisterPage() {
  const { session, loading } = useAuth()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({ resolver: zodResolver(registerSchema) })

  if (!loading && session) return <Navigate to={next} replace />

  async function onSubmit({ fullName, email, password }: RegisterValues) {
    setFormError(null)
    const { error } = await supabase.auth.signUp({
      email,
      password,
      options: {
        data: { full_name: fullName },
        emailRedirectTo: `${window.location.origin}/login?next=${encodeURIComponent(next)}`,
      },
    })
    if (error) {
      setFormError(error.message || 'We couldn’t create your account. Please try again.')
      return
    }
    setSentTo(email)
  }

  return (
    <AuthCard
      title="Create your account"
      subtitle="Book tickets in seconds and keep them all in one place."
      footer={
        <>
          Already have an account?{' '}
          <Link to={`/login?next=${encodeURIComponent(next)}`} className="font-medium text-primary hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      {sentTo ? (
        <CheckEmail email={sentTo}>Open it to confirm your email, then sign in.</CheckEmail>
      ) : (
        <div className="space-y-5">
          <GoogleButton next={next} />
          <OrDivider />
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <FormField label="Full name" autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
            <FormField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
            <FormField
              label="Password"
              type="password"
              autoComplete="new-password"
              hint="At least 8 characters, with a letter and a number."
              error={errors.password?.message}
              {...register('password')}
            />
            <FormField
              label="Confirm password"
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
              {isSubmitting ? 'Creating account…' : 'Create account'}
            </Button>
            <p className="text-center text-xs text-muted-foreground">
              By continuing you agree to our{' '}
              <Link to="/legal/terms" className="underline">
                Terms
              </Link>{' '}
              and{' '}
              <Link to="/legal/privacy" className="underline">
                Privacy Policy
              </Link>
              .
            </p>
          </form>
        </div>
      )}
    </AuthCard>
  )
}
