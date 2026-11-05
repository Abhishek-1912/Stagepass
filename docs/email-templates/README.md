# Supabase Auth email templates

Supabase sends sign-in, sign-up and password-reset emails itself (through Resend SMTP).
Paste these into **Supabase → Authentication → Emails → Templates**.

| Supabase template | Subject | Body file | Used when |
|---|---|---|---|
| **Magic Link** | `Your Stagepass sign-in code: {{ .Token }}` | `magic-link.html` | An existing user asks for an email code |
| **Confirm signup** | `Confirm your Stagepass account` | `confirm-signup.html` | A new user signs up (by email code or password) |
| **Reset Password** | `Reset your Stagepass password` | `reset-password.html` | "Forgot password?" |

`{{ .Token }}` is the 6-digit code; `{{ .ConfirmationURL }}` is the link. Keep both placeholders exactly as written.

Also check **Authentication → Providers → Email**: *Email OTP Length* = **6**.

App emails (welcome, booking confirmed, refunds…) are sent by the Stagepass API through the Resend API, not here.
