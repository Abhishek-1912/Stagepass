import { useEffect, type ReactNode } from 'react'

export interface LegalSection {
  id: string
  heading: string
  body: ReactNode
}

export const LAST_UPDATED = '8 November 2026'

/** Shared layout for the legal pages: title, last-updated line, contents list and numbered sections. */
export function LegalPage({ title, intro, sections }: { title: string; intro?: ReactNode; sections: LegalSection[] }) {
  useEffect(() => {
    document.title = `${title} · Stagepass`
  }, [title])

  return (
    <article className="mx-auto max-w-3xl px-4 py-12 sm:px-6">
      <h1 className="text-3xl font-bold sm:text-4xl">{title}</h1>
      <p className="mt-2 text-sm text-muted-foreground">Last updated: {LAST_UPDATED}</p>
      {intro && <div className="mt-6 space-y-3 leading-relaxed text-muted-foreground">{intro}</div>}
      <nav aria-label="Contents" className="mt-8 rounded-2xl border border-border bg-card p-5">
        <p className="mb-2 text-sm font-semibold">Contents</p>
        <ol className="list-decimal space-y-1 pl-5 text-sm">
          {sections.map((s) => (
            <li key={s.id}>
              <a href={`#${s.id}`} className="text-primary hover:underline">
                {s.heading}
              </a>
            </li>
          ))}
        </ol>
      </nav>
      <div className="mt-10 space-y-10">
        {sections.map((s, i) => (
          <section key={s.id} id={s.id} className="scroll-mt-24 space-y-3">
            <h2 className="text-xl font-bold">
              {i + 1}. {s.heading}
            </h2>
            <div className="space-y-3 leading-relaxed text-foreground/90 [&_li]:ml-5 [&_ul]:list-disc [&_ul]:space-y-1.5">
              {s.body}
            </div>
          </section>
        ))}
      </div>
    </article>
  )
}
