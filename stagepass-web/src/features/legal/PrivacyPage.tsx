import { useCompany } from './api'
import { ContactLink } from './CompanyContact'
import { LegalPage } from './LegalPage'

/** Privacy policy — lists what Stagepass actually stores and which services process it. */
export function PrivacyPage() {
  const { data: company } = useCompany()
  const name = company?.name ?? 'Stagepass'
  return (
    <LegalPage
      title="Privacy policy"
      intro={
        <p>
          This policy explains what personal information {name} collects through Stagepass, why, who it is shared with,
          and the choices you have.
        </p>
      }
      sections={[
        {
          id: 'collect',
          heading: 'What we collect',
          body: (
            <ul>
              <li><strong>Account:</strong> your name, email address, and if you add them, phone number, city and profile photo.</li>
              <li><strong>Bookings and tickets:</strong> what you booked, the attendee name and contact details you enter, your tickets and their check-in status.</li>
              <li><strong>Payments:</strong> payment amounts and status. Card, UPI and bank details are entered directly with Razorpay; Stagepass never sees or stores them.</li>
              <li><strong>Organizers:</strong> organization details, PAN, GSTIN and payout bank account. The bank account number is stored encrypted.</li>
              <li><strong>Activity:</strong> saved events, reviews, reports you send, notifications, and records of emails we send you.</li>
              <li><strong>Technical:</strong> basic logs (such as IP address and browser) to keep the service secure and working.</li>
            </ul>
          ),
        },
        {
          id: 'use',
          heading: 'How we use it',
          body: (
            <ul>
              <li>To create your account, sell and deliver tickets, check you in at the door and process refunds.</li>
              <li>To send emails you need: sign-in codes, booking confirmations, reminders, refund and event updates.</li>
              <li>To pay organizers and issue GST invoices.</li>
              <li>To prevent fraud and abuse, and to meet legal and tax obligations.</li>
            </ul>
          ),
        },
        {
          id: 'organizers',
          heading: 'What organizers see',
          body: (
            <p>
              When you book an event, its organizer sees the attendee name, email and phone you entered and your tickets&apos;
              check-in status, so they can run the event. Door staff see only the ticket code, holder name and ticket type
              when scanning. Reviews show your first name only.
            </p>
          ),
        },
        {
          id: 'processors',
          heading: 'Services that process data for us',
          body: (
            <ul>
              <li><strong>Supabase</strong> — database and sign-in.</li>
              <li><strong>Razorpay</strong> — payments and refunds.</li>
              <li><strong>Cloudinary</strong> — event images and profile photos.</li>
              <li><strong>Resend</strong> — sending emails.</li>
              <li><strong>Render</strong> — hosting the website and servers.</li>
            </ul>
          ),
        },
        {
          id: 'sharing',
          heading: 'Sharing',
          body: <p>We don&apos;t sell your personal information. We share it only as described here, or when the law requires.</p>,
        },
        {
          id: 'retention',
          heading: 'How long we keep it',
          body: (
            <p>
              We keep account information while your account is active, and booking, payment and invoice records for as
              long as tax and accounting laws require.
            </p>
          ),
        },
        {
          id: 'security',
          heading: 'Security',
          body: (
            <p>
              Data travels over HTTPS, payout bank details are encrypted, and access is limited by role. No system is
              perfectly secure, but we work to protect your information.
            </p>
          ),
        },
        {
          id: 'choices',
          heading: 'Your choices and rights',
          body: (
            <ul>
              <li>Update your profile, and turn reminder emails on or off, from your account page.</li>
              <li>Ask us for a copy of your information, to correct it, or to delete your account (we keep records the law requires).</li>
            </ul>
          ),
        },
        {
          id: 'contact',
          heading: 'Contact',
          body: (
            <p>
              For privacy questions or requests, write to us at <ContactLink company={company} />.
            </p>
          ),
        },
      ]}
    />
  )
}
