import { forwardRef, type ComponentProps, type ReactNode } from 'react'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

const control =
  'w-full rounded-xl border border-input bg-background px-3 text-sm shadow-xs outline-none transition-[color,box-shadow] focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 aria-invalid:border-destructive disabled:cursor-not-allowed disabled:opacity-50'

function Shell({ id, label, error, hint, children }: { id: string; label: string; error?: string; hint?: string; children: ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="text-sm text-destructive">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${id}-hint`} className="text-xs text-muted-foreground">
            {hint}
          </p>
        )
      )}
    </div>
  )
}

type FieldProps = { label: string; error?: string; hint?: string }

export const TextField = forwardRef<HTMLInputElement, ComponentProps<'input'> & FieldProps>(function TextField(
  { label, error, hint, id, name, className, ...props },
  ref,
) {
  const fieldId = id ?? name ?? label
  return (
    <Shell id={fieldId} label={label} error={error} hint={hint}>
      <input
        ref={ref}
        id={fieldId}
        name={name}
        aria-invalid={!!error}
        aria-describedby={error ? `${fieldId}-error` : hint ? `${fieldId}-hint` : undefined}
        className={cn(control, 'h-11', className)}
        {...props}
      />
    </Shell>
  )
})

export const TextAreaField = forwardRef<HTMLTextAreaElement, ComponentProps<'textarea'> & FieldProps>(
  function TextAreaField({ label, error, hint, id, name, className, ...props }, ref) {
    const fieldId = id ?? name ?? label
    return (
      <Shell id={fieldId} label={label} error={error} hint={hint}>
        <textarea
          ref={ref}
          id={fieldId}
          name={name}
          aria-invalid={!!error}
          className={cn(control, 'min-h-28 py-2.5', className)}
          {...props}
        />
      </Shell>
    )
  },
)

export const SelectField = forwardRef<HTMLSelectElement, ComponentProps<'select'> & FieldProps>(function SelectField(
  { label, error, hint, id, name, className, children, ...props },
  ref,
) {
  const fieldId = id ?? name ?? label
  return (
    <Shell id={fieldId} label={label} error={error} hint={hint}>
      <select
        ref={ref}
        id={fieldId}
        name={name}
        aria-invalid={!!error}
        className={cn(control, 'h-11 appearance-none bg-[length:16px] bg-[right_12px_center] bg-no-repeat pr-9', className)}
        style={{
          backgroundImage:
            "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%236b7280' stroke-width='2'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E\")",
        }}
        {...props}
      >
        {children}
      </select>
    </Shell>
  )
})

export function FormSection({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
  return (
    <section className="grid gap-6 border-b border-border/60 py-8 first:pt-0 last:border-0 md:grid-cols-[220px_1fr]">
      <div className="space-y-1">
        <h2 className="font-sans text-base font-semibold tracking-normal">{title}</h2>
        {description && <p className="text-sm text-muted-foreground">{description}</p>}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">{children}</div>
    </section>
  )
}
