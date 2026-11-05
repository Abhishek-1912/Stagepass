import { createClient } from '@supabase/supabase-js'
import { env } from './env'

/** Supabase is used only for authentication; all data goes through the Stagepass API. */
export const supabase = createClient(env.supabaseUrl, env.supabaseAnonKey, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
    detectSessionInUrl: true,
    flowType: 'pkce',
  },
})
