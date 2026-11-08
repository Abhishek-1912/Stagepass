import { config } from 'zod'

// Zod probes for `Function('')` support to speed up parsing; the site's Content-Security-Policy blocks that, and
// every page load would report a violation. jitless skips the probe. This module must be imported before anything
// that creates schemas (it is the first import in main.tsx).
config({ jitless: true })
