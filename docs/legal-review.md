# Legal pages: what your legal advisor should review

Stagepass has four public pages, linked from every page's footer:

| Page | Address | Source |
|---|---|---|
| About & contact | `/about` | `stagepass-web/src/features/legal/AboutPage.tsx` |
| Terms of use | `/legal/terms` | `.../legal/TermsPage.tsx` |
| Privacy policy | `/legal/privacy` | `.../legal/PrivacyPage.tsx` |
| Refund & cancellation policy | `/legal/refunds` | `.../legal/RefundsPage.tsx` |

The text was written to describe exactly how Stagepass works today. It is a starting point, **not legal advice**.
Please have it checked before turning on live payments (Razorpay reviews these pages during activation).

## Company details shown on the pages

The company name, address, email, phone and GSTIN come from the API's settings on Render (`COMPANY_NAME`,
`COMPANY_ADDRESS`, `COMPANY_EMAIL`, `COMPANY_PHONE`, `COMPANY_GSTIN`). Details that aren't set are hidden. Set at
least the name, address and email before applying to Razorpay.

## Points to confirm

1. **Legal entity and address** — the registered company name and address shown on `/about`.
2. **Grievance officer** — Indian e-commerce and IT rules may require naming a grievance officer with contact details
   and response times. Add them to the About page if required.
3. **Refund rules** — the Refunds page states: full refund (fee included) when an event is cancelled; a 7-day full
   refund window after a date/venue change; otherwise the event's own rule; convenience fee not refunded on
   attendee requests; 5–7 working days to arrive; used tickets not refundable. Confirm these match your contracts
   with organizers.
4. **GST and TDS** — the convenience fee carries 18% GST; organizer TDS (section 194-O) is configured at 0% until your
   accountant confirms the rate.
5. **Liability and governing law** — the Terms limit liability to the booking amount and choose Indian law; add a
   specific city for jurisdiction if you want one.
6. **Privacy** — the list of data stored, the processors (Supabase, Razorpay, Cloudinary, Resend, Render), retention,
   and the rights you offer under the Digital Personal Data Protection Act, 2023.
7. **Age** — the Terms set 18+ (or with a guardian's permission).

To change the text, edit the files above (or ask your developer); every push to `main` redeploys the site.
