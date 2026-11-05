/** Hard navigation to the login page, remembering where the user was. Isolated so tests can replace it. */
export function redirectToLogin(returnTo: string) {
  window.location.assign(`/login?next=${encodeURIComponent(returnTo)}`)
}
