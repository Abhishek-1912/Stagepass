import { Link } from 'react-router'
import { useCompany } from './api'
import { ContactLink } from './CompanyContact'
import { LegalPage } from './LegalPage'

/** Refund & cancellation rules — these mirror how Stagepass actually processes refunds. */
export function RefundsPage() {
  const { data: company } = useCompany()
  return (
    <LegalPage
      title="Refund & cancellation policy"
      intro={
        <p>
          This policy explains when you can get your money back for tickets bought on Stagepass. Each event page also
          shows that event&apos;s refund rule before you book.
        </p>
      }
      sections={[
        {
          id: 'cancelled',
          heading: 'If an event is cancelled',
          body: (
            <p>
              Every ticket is refunded in full — the ticket price, the convenience fee and the GST on it — automatically.
              You don&apos;t need to ask. We email you when the refund starts.
            </p>
          ),
        },
        {
          id: 'changed',
          heading: 'If the date, time or venue changes',
          body: (
            <p>
              We email every ticket holder. If you can no longer attend, you can cancel your tickets for a full refund,
              convenience fee included, for 7 days after we tell you about the change, from <strong>My tickets</strong>.
              After those 7 days, the event&apos;s normal refund rule applies.
            </p>
          ),
        },
        {
          id: 'event-rule',
          heading: 'Each event’s refund rule',
          body: (
            <>
              <p>Otherwise, the organizer chooses one of these rules for their event, and the event page shows it:</p>
              <ul>
                <li><strong>No refunds</strong> — tickets can&apos;t be refunded unless the event is cancelled or changed as above.</li>
                <li><strong>Refunds until a set time</strong> — for example, until 48 hours before the event starts.</li>
              </ul>
            </>
          ),
        },
        {
          id: 'within',
          heading: 'Refunds within the event’s rule',
          body: (
            <p>
              Open <strong>My tickets → your booking → Refund tickets</strong>. You can refund some or
              all of the tickets in a booking. Refunds within the rule are approved automatically and return the ticket
              price. The convenience fee and the GST on it are not refunded in this case.
            </p>
          ),
        },
        {
          id: 'outside',
          heading: 'Requests outside the rule',
          body: (
            <p>
              You can still ask with <strong>Request a refund</strong> on your booking; the request goes to the event&apos;s
              organizer, who may approve or decline it. We email you their decision.
            </p>
          ),
        },
        {
          id: 'used',
          heading: 'Used tickets',
          body: <p>Tickets that have been checked in at the event (used tickets) can&apos;t be refunded.</p>,
        },
        {
          id: 'timing',
          heading: 'How and when the money arrives',
          body: (
            <p>
              Refunds go back to the original payment method (UPI, card, net banking or wallet) through our payment
              partner Razorpay. They usually reach your account within 5–7 working days after the refund starts; your
              bank may take longer. Free tickets have nothing to refund — cancelling simply releases the seat.
            </p>
          ),
        },
        {
          id: 'help',
          heading: 'Help',
          body: (
            <p>
              If something looks wrong with a refund, write to us at <ContactLink company={company} /> with your booking
              code. See also our <Link to="/legal/terms" className="text-primary hover:underline">Terms of use</Link>.
            </p>
          ),
        },
      ]}
    />
  )
}
