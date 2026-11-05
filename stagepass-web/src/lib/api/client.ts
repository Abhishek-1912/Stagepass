import { env } from '@/lib/env'
import { redirectToLogin } from '@/lib/navigation'
import { supabase } from '@/lib/supabase'
import { ApiError } from './ApiError'

export type ApiInit = RequestInit & { json?: unknown }

async function currentToken(): Promise<string | undefined> {
  const { data } = await supabase.auth.getSession()
  return data.session?.access_token
}

function buildRequest(init: ApiInit, token: string | undefined): RequestInit {
  const { json, headers: initHeaders, ...rest } = init
  const headers = new Headers(initHeaders)
  headers.set('Accept', 'application/json, application/problem+json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  let body = rest.body
  if (json !== undefined) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(json)
  }
  return { ...rest, headers, body }
}

async function send(url: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(url, init)
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Can’t reach Stagepass right now. Check your connection and try again.')
  }
}

async function parse<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T
  const text = await response.text()
  const body: unknown = text ? JSON.parse(text) : undefined
  if (!response.ok) throw ApiError.fromProblem(response.status, body)
  return body as T
}

/**
 * Calls the Stagepass API (`/api/v1` + path). Attaches the Supabase access token when signed in.
 * On 401 it refreshes the session once and retries; if that fails the user is signed out and sent to login.
 */
export async function api<T>(path: string, init: ApiInit = {}): Promise<T> {
  const url = `${env.apiBaseUrl}/api/v1${path}`
  const token = await currentToken()
  let response = await send(url, buildRequest(init, token))

  if (response.status === 401 && token) {
    const { data, error } = await supabase.auth.refreshSession()
    const fresh = data.session?.access_token
    if (!error && fresh) {
      response = await send(url, buildRequest(init, fresh))
    }
    if (response.status === 401) {
      await supabase.auth.signOut()
      redirectToLogin(window.location.pathname + window.location.search)
    }
  }

  return parse<T>(response)
}
