import { zodResolver } from '@hookform/resolvers/zod'
import { BadgeCheck, Clock, Sparkles } from 'lucide-react'
import { Controller, useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { FormSection, SelectField, TextAreaField, TextField } from '@/components/form/fields'
import { Button } from '@/components/ui/button'
import { ImageUploader } from '@/components/upload/ImageUploader'
import { isApiError } from '@/lib/api/ApiError'
import type { OrganizerApplication } from '@/lib/api/types'
import { useApply, useMyApplication } from './api'
import {
  type ApplicationFormInput,
  type ApplicationFormValues,
  applicationSchema,
  INDIAN_STATES,
} from './applicationSchema'

function defaults(a?: OrganizerApplication): ApplicationFormInput {
  return {
    orgName: a?.orgName ?? '',
    bio: a?.bio ?? '',
    logoUrl: a?.logoUrl ?? undefined,
    contactEmail: a?.contactEmail ?? '',
    contactPhone: a?.contactPhone ?? '',
    website: a?.website ?? '',
    pan: a?.pan ?? '',
    gstin: a?.gstin ?? '',
    state: a?.state ?? '',
    payoutAccountName: a?.payoutAccountName ?? '',
    payoutAccountNumber: '',
    payoutIfsc: a?.payoutIfsc ?? '',
  }
}

function StatusCard({ icon: Icon, title, children }: { icon: typeof Clock; title: string; children: React.ReactNode }) {
  return (
    <div className="mx-auto max-w-xl space-y-4 rounded-3xl border border-border bg-card p-8 text-center shadow-[var(--shadow-card)]">
      <span className="mx-auto grid size-12 place-items-center rounded-2xl bg-accent text-accent-foreground">
        <Icon className="size-6" />
      </span>
      <h1 className="text-2xl font-bold">{title}</h1>
      <div className="space-y-4 text-muted-foreground">{children}</div>
    </div>
  )
}

function ApplicationForm({ existing }: { existing?: OrganizerApplication }) {
  const apply = useApply()
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ApplicationFormInput, unknown, ApplicationFormValues>({
    resolver: zodResolver(applicationSchema),
    defaultValues: defaults(existing),
  })

  async function onSubmit(values: ApplicationFormValues) {
    try {
      await apply.mutateAsync(values)
      toast.success('Application sent. We’ll email you once it’s reviewed.')
    } catch (e) {
      if (isApiError(e) && e.fieldErrors.length) {
        e.fieldErrors.forEach((f) => setError(f.field as keyof ApplicationFormInput, { message: f.message }))
      } else {
        toast.error(isApiError(e) ? e.detail : 'Could not send your application.')
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="rounded-3xl border border-border bg-card p-6 shadow-[var(--shadow-card)] sm:p-8">
      <FormSection title="Organization" description="How attendees will see you on event pages.">
        <div className="sm:col-span-2">
          <TextField label="Organization name" error={errors.orgName?.message} {...register('orgName')} />
        </div>
        <div className="sm:col-span-2">
          <TextAreaField label="About" hint="A few lines about the events you run." error={errors.bio?.message} {...register('bio')} />
        </div>
        <Controller
          control={control}
          name="logoUrl"
          render={({ field }) => (
            <ImageUploader
              purpose="ORGANIZER_LOGO"
              aspect="square"
              label="Logo"
              hint="Square, JPG/PNG/WebP, up to 5 MB"
              value={field.value}
              onChange={(url) => field.onChange(url ?? undefined)}
            />
          )}
        />
        <TextField label="Website" type="url" placeholder="https://" error={errors.website?.message} {...register('website')} />
      </FormSection>

      <FormSection title="Contact" description="Where we send approvals and payout updates.">
        <TextField label="Contact email" type="email" autoComplete="email" error={errors.contactEmail?.message} {...register('contactEmail')} />
        <TextField label="Mobile number" inputMode="numeric" placeholder="98765 43210" error={errors.contactPhone?.message} {...register('contactPhone')} />
      </FormSection>

      <FormSection title="Tax details" description="Needed for invoices and TDS. Your PAN is never shown publicly.">
        <TextField label="PAN" autoCapitalize="characters" placeholder="ABCDE1234F" error={errors.pan?.message} {...register('pan')} />
        <TextField label="GSTIN (optional)" autoCapitalize="characters" error={errors.gstin?.message} {...register('gstin')} />
        <SelectField label="State" error={errors.state?.message} {...register('state')}>
          <option value="">Select state</option>
          {INDIAN_STATES.map((s) => (
            <option key={s}>{s}</option>
          ))}
        </SelectField>
      </FormSection>

      <FormSection title="Payouts" description="Ticket revenue is paid to this account after each event. Stored encrypted.">
        <TextField label="Account holder name" error={errors.payoutAccountName?.message} {...register('payoutAccountName')} />
        <TextField
          label="Bank account number"
          inputMode="numeric"
          autoComplete="off"
          hint={existing?.payoutAccountMasked ? `Currently ${existing.payoutAccountMasked} — enter it again to reapply.` : undefined}
          error={errors.payoutAccountNumber?.message}
          {...register('payoutAccountNumber')}
        />
        <TextField label="IFSC" autoCapitalize="characters" placeholder="HDFC0001234" error={errors.payoutIfsc?.message} {...register('payoutIfsc')} />
      </FormSection>

      <div className="flex flex-col-reverse gap-3 pt-6 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-xs text-muted-foreground">
          By applying you agree to the{' '}
          <Link to="/legal/terms" className="underline">
            organizer terms
          </Link>
          .
        </p>
        <Button type="submit" size="lg" className="rounded-full px-6" disabled={isSubmitting}>
          {isSubmitting ? 'Sending…' : 'Submit application'}
        </Button>
      </div>
    </form>
  )
}

export function BecomeOrganizerPage() {
  const { data: application, isPending, error } = useMyApplication()
  const none = isApiError(error) && error.status === 404

  if (isPending) return <PageLoader />

  return (
    <div className="mx-auto max-w-4xl space-y-8 px-4 py-12 sm:px-6">
      {application?.status === 'PENDING' && (
        <StatusCard icon={Clock} title="Your application is under review">
          <p>
            Thanks, {application.orgName}! Our team usually reviews applications within 2 business days. We’ll email{' '}
            {application.contactEmail} when there’s an update.
          </p>
        </StatusCard>
      )}

      {application?.status === 'APPROVED' && (
        <StatusCard icon={BadgeCheck} title="You’re an approved organizer">
          <p>Create events, set ticket tiers and start selling.</p>
          <Button asChild className="rounded-full">
            <Link to="/organizer">Open organizer dashboard</Link>
          </Button>
        </StatusCard>
      )}

      {application?.status === 'SUSPENDED' && (
        <StatusCard icon={Clock} title="Your organizer account is suspended">
          <p>Contact support to find out more.</p>
        </StatusCard>
      )}

      {(none || application?.status === 'REJECTED') && (
        <>
          <header className="space-y-3">
            <span className="inline-flex items-center gap-2 rounded-full bg-accent px-3 py-1 text-xs font-medium text-accent-foreground">
              <Sparkles className="size-3.5" /> Free to list · 0% commission at launch
            </span>
            <h1 className="text-3xl font-bold sm:text-4xl">Sell tickets on Stagepass</h1>
            <p className="max-w-2xl text-muted-foreground">
              Tell us about your organization. Once approved you can publish events, sell tickets and check guests in
              with your phone.
            </p>
          </header>
          {application?.status === 'REJECTED' && (
            <div role="status" className="rounded-2xl border border-destructive/30 bg-destructive/5 p-4 text-sm">
              <p className="font-medium text-destructive">We couldn’t approve your last application:</p>
              <p className="mt-1">{application.rejectionReason}</p>
              <p className="mt-2 text-muted-foreground">Update the details below and submit again.</p>
            </div>
          )}
          <ApplicationForm existing={application} />
        </>
      )}

      {error && !none && (
        <p role="alert" className="rounded-xl bg-destructive/10 p-4 text-destructive">
          {isApiError(error) ? error.detail : 'Could not load your application.'}
        </p>
      )}
    </div>
  )
}
