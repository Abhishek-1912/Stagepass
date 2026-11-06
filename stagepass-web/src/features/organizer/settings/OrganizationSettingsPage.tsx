import { zodResolver } from '@hookform/resolvers/zod'
import { ExternalLink } from 'lucide-react'
import { useForm, useWatch } from 'react-hook-form'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { FormSection, TextAreaField, TextField } from '@/components/form/fields'
import { Button } from '@/components/ui/button'
import { ImageUploader } from '@/components/upload/ImageUploader'
import { isApiError } from '@/lib/api/ApiError'
import { type OrganizationSettings, type SettingsUpdate, useOrganizationSettings, useUpdateOrganizationSettings } from './api'
import { type SettingsForm, settingsSchema } from './settingsSchema'

const EDITABLE = ['bio', 'logoUrl', 'website', 'contactEmail', 'contactPhone', 'payoutAccountName'] as const

function defaults(s: OrganizationSettings): SettingsForm {
  return {
    bio: s.bio ?? '',
    logoUrl: s.logoUrl ?? '',
    website: s.website ?? '',
    contactEmail: s.contactEmail ?? '',
    contactPhone: s.contactPhone ?? '',
    payoutAccountName: s.payoutAccountName ?? '',
    changeAccount: false,
    payoutAccountNumber: '',
    payoutIfsc: s.payoutIfsc ?? '',
  }
}

function ReadOnly({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="space-y-1">
      <p className="text-sm font-medium">{label}</p>
      <p className="text-sm text-muted-foreground">{value ?? '—'}</p>
    </div>
  )
}

function SettingsFormView({ settings }: { settings: OrganizationSettings }) {
  const update = useUpdateOrganizationSettings()
  const form = useForm<SettingsForm>({ resolver: zodResolver(settingsSchema), defaultValues: defaults(settings) })
  const { register, handleSubmit, formState, control, setValue, setError, reset } = form
  const changeAccount = useWatch({ control, name: 'changeAccount' })
  const logoUrl = useWatch({ control, name: 'logoUrl' })
  // Read during render so react-hook-form tracks which fields changed.
  const { dirtyFields } = formState

  const submit = handleSubmit(async (v) => {
    const body: SettingsUpdate = {}
    for (const k of EDITABLE) {
      if (dirtyFields[k]) body[k] = v[k].trim()
    }
    if (v.changeAccount) {
      body.payoutAccountNumber = v.payoutAccountNumber.replace(/\s+/g, '')
      body.payoutIfsc = v.payoutIfsc.trim().toUpperCase()
    }
    if (Object.keys(body).length === 0) {
      toast('Nothing to save')
      return
    }
    try {
      const saved = await update.mutateAsync(body)
      reset(defaults(saved))
      toast.success('Organization details saved')
    } catch (e) {
      if (isApiError(e) && e.fieldErrors.length > 0) {
        for (const fe of e.fieldErrors) setError(fe.field as keyof SettingsForm, { message: fe.message })
      } else {
        toast.error(isApiError(e) ? e.detail : 'Could not save')
      }
    }
  })

  const err = (k: keyof SettingsForm) => formState.errors[k]?.message

  return (
    <form onSubmit={submit} noValidate className="space-y-8">
      <FormSection title="Organization" description="Shown on your public organizer page.">
        <div className="grid gap-4 sm:grid-cols-2">
          <ReadOnly label="Name" value={settings.orgName} />
          <div className="space-y-1">
            <p className="text-sm font-medium">Public page</p>
            <Link to={`/o/${settings.slug}`} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-sm text-primary hover:underline">
              /o/{settings.slug} <ExternalLink className="size-3.5" aria-hidden />
            </Link>
          </div>
        </div>
        <ImageUploader
          purpose="ORGANIZER_LOGO"
          aspect="square"
          label="Logo"
          value={logoUrl || null}
          onChange={(url) => setValue('logoUrl', url ?? '', { shouldDirty: true })}
        />
        {err('logoUrl') && <p className="text-sm text-destructive">{err('logoUrl')}</p>}
        <TextAreaField label="About your organization" rows={4} {...register('bio')} error={err('bio')} />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Website" placeholder="https://" {...register('website')} error={err('website')} />
          <TextField label="Contact email" type="email" {...register('contactEmail')} error={err('contactEmail')} />
          <TextField label="Contact phone" inputMode="numeric" maxLength={10} {...register('contactPhone')} error={err('contactPhone')} />
        </div>
      </FormSection>

      <FormSection title="Payout bank account" description="Where Stagepass sends your earnings.">
        <TextField label="Account holder name" {...register('payoutAccountName')} error={err('payoutAccountName')} />
        {changeAccount ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Account number" inputMode="numeric" autoComplete="off" {...register('payoutAccountNumber')} error={err('payoutAccountNumber')} />
            <TextField label="IFSC" autoCapitalize="characters" {...register('payoutIfsc')} error={err('payoutIfsc')} />
          </div>
        ) : (
          <div className="flex flex-wrap items-center gap-3">
            <p className="text-sm">
              Account <span className="font-mono">{settings.payoutAccountMasked ?? 'not set'}</span>
              {settings.payoutIfsc && <span className="text-muted-foreground"> · {settings.payoutIfsc}</span>}
            </p>
            <Button type="button" variant="outline" size="sm" className="rounded-full" onClick={() => setValue('changeAccount', true)}>
              Change bank account
            </Button>
          </div>
        )}
      </FormSection>

      <FormSection title="Tax details" description="Contact support to change these.">
        <div className="grid gap-4 sm:grid-cols-2">
          <ReadOnly label="PAN" value={settings.pan} />
          <ReadOnly label="GSTIN" value={settings.gstin} />
        </div>
      </FormSection>

      <Button type="submit" className="rounded-full" disabled={update.isPending}>
        {update.isPending ? 'Saving…' : 'Save changes'}
      </Button>
    </form>
  )
}

/** `/organizer/settings` — public profile, contact details and payout bank account. */
export function OrganizationSettingsPage() {
  const settings = useOrganizationSettings()
  if (settings.isPending || !settings.data) return <PageLoader label="Loading settings" />
  return (
    <div className="max-w-3xl space-y-6">
      <h2 className="text-2xl font-bold">Organization settings</h2>
      <SettingsFormView settings={settings.data} />
    </div>
  )
}
