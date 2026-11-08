import { useEffect } from 'react'
import { Link } from 'react-router'
import { useCompany } from './api'
import { CompanyContact } from './CompanyContact'

/** Who runs Stagepass and how to reach them. */
export function AboutPage() {
  const { data: company } = useCompany()
  const name = company?.name ?? 'Stagepass'

  useEffect(() => {
    document.title = 'About & contact · Stagepass'
  }, [])

  return (
    <article className="mx-auto max-w-3xl space-y-8 px-4 py-12 sm:px-6">
      <div className="space-y-3">
        <h1 className="text-3xl font-bold sm:text-4xl">About &amp; contact</h1>
        <p className="leading-relaxed text-muted-foreground">
          Stagepass is an online ticketing platform for events in India — concerts, comedy, tech meetups, workshops,
          sports and food festivals. Organizers create and sell tickets for their events on Stagepass; attendees discover
          events, book tickets, pay securely and enter with a QR ticket on their phone.
        </p>
        <p className="leading-relaxed text-muted-foreground">
          Stagepass is operated by <strong className="text-foreground">{name}</strong>. Each event is organised by the
          organizer named on its page, who is responsible for running the event.
        </p>
      </div>

      <section className="space-y-3">
        <h2 className="text-xl font-bold">Contact us</h2>
        <CompanyContact company={company} />
        <p className="text-sm text-muted-foreground">
          For questions about a specific event, contact its organizer using the details on the event or organizer page.
          For help with a booking, payment or refund, write to us and include your booking code (for example
          SP-7K2M9Q).
        </p>
      </section>

      <section className="space-y-2">
        <h2 className="text-xl font-bold">Policies</h2>
        <ul className="list-disc space-y-1 pl-5 text-sm">
          <li><Link to="/legal/terms" className="text-primary hover:underline">Terms of use</Link></li>
          <li><Link to="/legal/privacy" className="text-primary hover:underline">Privacy policy</Link></li>
          <li><Link to="/legal/refunds" className="text-primary hover:underline">Refund &amp; cancellation policy</Link></li>
        </ul>
      </section>
    </article>
  )
}
