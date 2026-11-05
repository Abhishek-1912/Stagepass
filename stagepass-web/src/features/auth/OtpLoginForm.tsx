import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Button } from '@/components/ui/button'
import { supabase } from '@/lib/supabase'
import { FormField } from './FormField'
import { type ForgotValues, forgotSchema, type OtpCodeValues, otpCodeSchema } from './schemas'

const RESEND_COOLDOWN_SECONDS = 60

function requestErrorMessage(error: { status?: number; message?: string }) {
  if (error.status === 429 || /rate limit/i.test(error.message ?? '')) {
    return 'Too many codes requested. Please wait a minute and try again.'
  }
  return 'We couldn’t send a code right now. Please try again.'
}

/**
 * Passwordless sign-in: Supabase emails a 6-digit code (through Resend SMTP), the user types it in.
 * Unknown emails get an account created, so this doubles as sign-up.
 */
export function OtpLoginForm({ onSignedIn }: { onSignedIn: () => void }) {
  const [email, setEmail] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cooldown, setCooldown] = useState(0)

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown((s) => s - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  const emailForm = useForm<ForgotValues>({ resolver: zodResolver(forgotSchema) })
  const codeForm = useForm<OtpCodeValues>({ resolver: zodResolver(otpCodeSchema) })

  async function sendCode(address: string) {
    setError(null)
    const { error: sendError } = await supabase.auth.signInWithOtp({
      email: address,
      options: { shouldCreateUser: true },
    })
    if (sendError) {
      setError(requestErrorMessage(sendError as { status?: number; message?: string }))
      return
    }
    setEmail(address)
    setCooldown(RESEND_COOLDOWN_SECONDS)
  }

  async function verify({ code }: OtpCodeValues) {
    if (!email) return
    setError(null)
    const { error: verifyError } = await supabase.auth.verifyOtp({ email, token: code, type: 'email' })
    if (verifyError) {
      setError('That code is incorrect or has expired. Check the latest email or request a new code.')
      return
    }
    onSignedIn()
  }

  const errorBox = error && (
    <p role="alert" className="rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
      {error}
    </p>
  )

  if (!email) {
    return (
      <form onSubmit={emailForm.handleSubmit(({ email: e }) => sendCode(e))} className="space-y-4" noValidate>
        <FormField
          label="Email"
          type="email"
          autoComplete="email"
          error={emailForm.formState.errors.email?.message}
          {...emailForm.register('email')}
        />
        {errorBox}
        <Button type="submit" size="lg" className="h-11 w-full rounded-xl" disabled={emailForm.formState.isSubmitting}>
          {emailForm.formState.isSubmitting ? 'Sending…' : 'Email me a code'}
        </Button>
        <p className="text-center text-xs text-muted-foreground">New here? We’ll create your account automatically.</p>
      </form>
    )
  }

  return (
    <form onSubmit={codeForm.handleSubmit(verify)} className="space-y-4" noValidate>
      <p className="text-sm text-muted-foreground">
        We sent a 6-digit code to <span className="font-medium text-foreground">{email}</span>. It expires in a few
        minutes.
      </p>
      <FormField
        label="6-digit code"
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        autoFocus
        className="h-12 rounded-xl text-center font-mono text-xl tracking-[0.5em]"
        error={codeForm.formState.errors.code?.message}
        {...codeForm.register('code')}
      />
      {errorBox}
      <Button type="submit" size="lg" className="h-11 w-full rounded-xl" disabled={codeForm.formState.isSubmitting}>
        {codeForm.formState.isSubmitting ? 'Verifying…' : 'Verify and sign in'}
      </Button>
      <div className="flex items-center justify-between text-sm">
        <button
          type="button"
          className="text-muted-foreground hover:text-foreground"
          onClick={() => {
            setEmail(null)
            setError(null)
            codeForm.reset()
          }}
        >
          Use a different email
        </button>
        <button
          type="button"
          disabled={cooldown > 0}
          onClick={() => sendCode(email)}
          className="font-medium text-primary hover:underline disabled:cursor-not-allowed disabled:text-muted-foreground disabled:no-underline"
        >
          {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
        </button>
      </div>
    </form>
  )
}
