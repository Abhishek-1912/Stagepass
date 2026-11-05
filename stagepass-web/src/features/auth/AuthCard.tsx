import type { ReactNode } from 'react'
import { Logo } from '@/components/brand/Logo'
import { placeholderImages } from '@/lib/images'

/** Split-screen auth layout: form on the left, event photography on the right (md+). */
export function AuthCard({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string
  subtitle?: string
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <div className="grid min-h-dvh md:grid-cols-2">
      <div className="flex flex-col px-6 py-8 sm:px-12">
        <Logo />
        <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center gap-8 py-12">
          <div className="space-y-2">
            <h1 className="text-3xl font-bold">{title}</h1>
            {subtitle && <p className="text-muted-foreground">{subtitle}</p>}
          </div>
          {children}
          {footer && <div className="text-center text-sm text-muted-foreground">{footer}</div>}
        </div>
      </div>
      <div className="relative hidden overflow-hidden md:block">
        <img src={placeholderImages.stageLights} alt="" className="absolute inset-0 size-full object-cover" />
        <div className="absolute inset-0 bg-[linear-gradient(160deg,rgb(108_76_241/0.75),rgb(55_125_255/0.35)_50%,rgb(11_12_16/0.85))]" />
        <div className="absolute inset-x-10 bottom-12 space-y-3 text-white">
          <p className="font-display text-3xl font-bold leading-tight">Every great night has a ticket.</p>
          <p className="max-w-sm text-white/75">
            Thousands of concerts, comedy shows and meetups, with instant QR tickets.
          </p>
        </div>
      </div>
    </div>
  )
}
