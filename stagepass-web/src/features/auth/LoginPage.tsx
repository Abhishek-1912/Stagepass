import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { OtpLoginForm } from './OtpLoginForm'
import { supabase } from '@/lib/supabase'
import { AuthCard } from './AuthCard'
import { useAuth } from './AuthProvider'
import { FormField } from './FormField'
import { GoogleButton, OrDivider } from './GoogleButton'
import { safeNext } from './safeNext'
import { type LoginValues, loginSchema } from './schemas'

export function LoginPage() {
  const { session, loading } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) })

  if (!loading && session) return <Navigate to={next} replace />

  async function onSubmit(values: LoginValues) {
    setFormError(null)
    const { error } = await supabase.auth.signInWithPassword(values)
    if (error) {
      setFormError(
        error.message.toLowerCase().includes('email not confirmed')
          ? 'Please confirm your email first. Check your inbox for the link.'
          : 'That email and password don’t match. Try again or reset your password.',
      )
      return
    }
    navigate(next, { replace: true })
  }

  return (
    <AuthCard
      title="Welcome back"
      subtitle="Sign in to see your tickets and book faster."
      footer={
        <>
          New to Stagepass?{' '}
          <Link to={`/register?next=${encodeURIComponent(next)}`} className="font-medium text-primary hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <div className="space-y-5">
        <GoogleButton next={next} />
        <OrDivider />
        <Tabs defaultValue="code" className="gap-5">
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="code">Email code</TabsTrigger>
            <TabsTrigger value="password">Password</TabsTrigger>
          </TabsList>
          <TabsContent value="code">
            <OtpLoginForm onSignedIn={() => navigate(next, { replace: true })} />
          </TabsContent>
          <TabsContent value="password">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <FormField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
          <div className="space-y-1.5">
            <FormField
              label="Password"
              type="password"
              autoComplete="current-password"
              error={errors.password?.message}
              {...register('password')}
            />
            <div className="text-right">
              <Link to="/forgot-password" className="text-sm text-primary hover:underline">
                Forgot password?
              </Link>
            </div>
          </div>
          {formError && (
            <p role="alert" className="rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {formError}
            </p>
          )}
          <Button type="submit" size="lg" className="h-11 w-full rounded-xl" disabled={isSubmitting}>
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </Button>
        </form>
          </TabsContent>
        </Tabs>
      </div>
    </AuthCard>
  )
}
