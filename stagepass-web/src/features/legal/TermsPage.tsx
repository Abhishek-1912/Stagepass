import { Link } from 'react-router'
import { useCompany } from './api'
import { ContactLink } from './CompanyContact'
import { LegalPage } from './LegalPage'

/** Terms of use for attendees and organizers. Starting text for the company's legal advisor to review. */
export function TermsPage() {
  const { data: company } = useCompany()
  const name = company?.name ?? 'Stagepass'
  return (
    <LegalPage
      title="Terms of use"
      intro={
        <p>
          These terms apply when you use Stagepass, the ticketing platform operated by {name} (&quot;Stagepass&quot;,
          &quot;we&quot;, &quot;us&quot;). By creating an account, buying a ticket or listing an event, you agree to them.
        </p>
      }
      sections={[
        {
          id: 'service',
          heading: 'What Stagepass does',
          body: (
            <p>
              Stagepass lets organizers list events and sell tickets, and lets attendees find events, book and pay for
              tickets, and enter with a QR ticket. Unless stated otherwise, the organizer — not Stagepass — runs the
              event and is responsible for it, including its content, safety, timing and any age or entry rules.
            </p>
          ),
        },
        {
          id: 'account',
          heading: 'Your account',
          body: (
            <ul>
              <li>Give accurate details and keep access to your email address secure; you are responsible for activity on your account.</li>
              <li>You must be at least 18, or use Stagepass with a parent or guardian&apos;s permission.</li>
              <li>We may suspend accounts that break these terms, misuse the platform or are involved in fraud.</li>
            </ul>
          ),
        },
        {
          id: 'buying',
          heading: 'Buying tickets',
          body: (
            <ul>
              <li>Prices are in Indian rupees. The checkout shows the ticket price, Stagepass&apos;s convenience fee and the GST on that fee before you pay.</li>
              <li>Seats are held for a few minutes while you pay. A booking is confirmed only when the payment is confirmed; you then receive your tickets by email and in <strong>My tickets</strong>.</li>
              <li>Payments are processed by Razorpay. Stagepass never sees your card or UPI details.</li>
              <li>Organizers may limit how many tickets one person can buy.</li>
            </ul>
          ),
        },
        {
          id: 'tickets',
          heading: 'Tickets and entry',
          body: (
            <ul>
              <li>Each ticket has a unique QR code that can be used for entry once. Don&apos;t share screenshots of it — the first scan wins.</li>
              <li>Reselling tickets above face value, or copying or altering them, is not allowed; such tickets may be cancelled without a refund.</li>
              <li>The organizer and venue may refuse entry under their own rules (for example ID or age checks).</li>
            </ul>
          ),
        },
        {
          id: 'refunds',
          heading: 'Cancellations and refunds',
          body: (
            <p>
              Refunds follow our <Link to="/legal/refunds" className="text-primary hover:underline">Refund &amp; cancellation policy</Link>, including full refunds when an event is cancelled.
            </p>
          ),
        },
        {
          id: 'organizers',
          heading: 'For organizers',
          body: (
            <ul>
              <li>Organizer accounts are reviewed before approval, and each event is reviewed before it goes on sale.</li>
              <li>You must have the right to hold and sell tickets for your event, describe it accurately and run it as advertised.</li>
              <li>Ticket revenue, less any refunds and commission, is paid to your registered bank account after the event, as shown on your Payouts page. Amounts refunded after a payout are deducted from later payouts.</li>
              <li>If you cancel an event, every ticket holder is refunded in full.</li>
              <li>Keep attendees&apos; personal details confidential and use them only to run your event.</li>
            </ul>
          ),
        },
        {
          id: 'conduct',
          heading: 'Acceptable use',
          body: (
            <p>
              Don&apos;t misuse Stagepass: no fake events, no automated buying or scraping, no attempts to break or overload
              the service, and nothing unlawful, misleading or offensive. You can report an event from its page.
            </p>
          ),
        },
        {
          id: 'liability',
          heading: 'Responsibility',
          body: (
            <p>
              We work to keep Stagepass available and accurate, but we can&apos;t guarantee it will always be uninterrupted.
              To the extent the law allows, Stagepass is not liable for how an organizer runs an event, and our total
              liability for a booking is limited to the amount you paid for it. Nothing in these terms limits rights you
              have under Indian consumer law.
            </p>
          ),
        },
        {
          id: 'changes',
          heading: 'Changes to these terms',
          body: <p>We may update these terms; the date above shows the latest version. Important changes will be shown on the site.</p>,
        },
        {
          id: 'law',
          heading: 'Governing law and contact',
          body: (
            <p>
              These terms are governed by the laws of India. For any question or complaint, write to us at{' '}
              <ContactLink company={company} />.
            </p>
          ),
        },
      ]}
    />
  )
}
