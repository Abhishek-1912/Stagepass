import type { CompanyInfo } from './api'

/** The company's contact card; any detail that isn't set is left out entirely. */
export function CompanyContact({ company }: { company: CompanyInfo | undefined }) {
  const name = company?.name ?? 'Stagepass'
  const rows: { label: string; value: React.ReactNode }[] = []
  if (company?.address) rows.push({ label: 'Address', value: company.address })
  if (company?.email) rows.push({ label: 'Email', value: <a href={`mailto:${company.email}`} className="text-primary hover:underline">{company.email}</a> })
  if (company?.phone) rows.push({ label: 'Phone', value: <a href={`tel:${company.phone.replace(/\s+/g, '')}`} className="text-primary hover:underline">{company.phone}</a> })
  if (company?.gstin) rows.push({ label: 'GSTIN', value: company.gstin })

  return (
    <div className="rounded-2xl border border-border bg-card p-5">
      <p className="font-semibold">{name}</p>
      {rows.length > 0 && (
        <dl className="mt-3 grid grid-cols-[6rem_1fr] gap-y-2 text-sm">
          {rows.map((r) => (
            <div key={r.label} className="contents">
              <dt className="text-muted-foreground">{r.label}</dt>
              <dd>{r.value}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  )
}

/** "write to us at …" link: the email when set, otherwise the About page. */
export function ContactLink({ company }: { company: CompanyInfo | undefined }) {
  return company?.email ? (
    <a href={`mailto:${company.email}`} className="text-primary hover:underline">{company.email}</a>
  ) : (
    <a href="/about" className="text-primary hover:underline">our contact page</a>
  )
}
