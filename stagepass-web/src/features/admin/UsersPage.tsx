import { useState } from 'react'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { useMe } from '@/features/auth/useMe'
import { Pager, SearchBox, StatusFilter } from '@/features/organizer/manage/ListControls'
import { isApiError } from '@/lib/api/ApiError'
import { AdminTable, EmptyState, StatusPill, Td, Th } from './AdminTable'
import { istDate } from './format'
import { ConfirmAction } from './ConfirmAction'
import { useAdminUsers, useReactivateUser, useSetAdmin, useSuspendUser } from './api'
import type { AdminUser } from './types'

const ROLE_OPTIONS = [
  { value: '', label: 'All roles' },
  { value: 'ADMIN', label: 'Admins' },
  { value: 'ORGANIZER', label: 'Organizers' },
  { value: 'ATTENDEE', label: 'Attendees' },
]
const STATUS_OPTIONS = [
  { value: '', label: 'Any status' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'SUSPENDED', label: 'Suspended' },
]

function fail(e: unknown, fallback: string): never {
  toast.error(isApiError(e) ? e.detail : fallback)
  throw e
}

function UserActions({ u, isMe }: { u: AdminUser; isMe: boolean }) {
  const suspend = useSuspendUser()
  const reactivate = useReactivateUser()
  const setAdmin = useSetAdmin()
  const name = u.fullName ?? u.email
  const isAdmin = u.roles.includes('ADMIN')

  if (isMe) return <span className="text-xs text-muted-foreground">You</span>
  return (
    <div className="flex flex-wrap justify-end gap-2">
      {u.status === 'SUSPENDED' ? (
        <ConfirmAction
          trigger={<Button size="sm" variant="outline">Reactivate</Button>}
          title={`Reactivate ${name}?`}
          effect="They can sign in and book again straight away."
          confirmLabel="Reactivate account"
          onConfirm={async () => {
            try {
              await reactivate.mutateAsync(u.id)
              toast.success(`${name} reactivated`)
            } catch (e) {
              fail(e, 'Could not reactivate')
            }
          }}
        />
      ) : isAdmin ? (
        <ConfirmAction
          trigger={<Button size="sm" variant="outline">Remove admin</Button>}
          title={`Remove admin from ${name}?`}
          effect="They lose access to the admin area on their next request."
          confirmLabel="Remove admin"
          destructive
          onConfirm={async () => {
            try {
              await setAdmin.mutateAsync({ id: u.id, admin: false })
              toast.success(`${name} is no longer an admin`)
            } catch (e) {
              fail(e, 'Could not remove admin')
            }
          }}
        />
      ) : (
        <>
          <ConfirmAction
            trigger={<Button size="sm" variant="outline">Make admin</Button>}
            title={`Make ${name} an admin?`}
            effect="They will see every user, payment and payout, and can change platform fees."
            confirmLabel="Make admin"
            onConfirm={async () => {
              try {
                await setAdmin.mutateAsync({ id: u.id, admin: true })
                toast.success(`${name} is now an admin`)
              } catch (e) {
                fail(e, 'Could not make admin')
              }
            }}
          />
          <ConfirmAction
            trigger={<Button size="sm" variant="destructive">Suspend</Button>}
            title={`Suspend ${name}?`}
            effect="They are signed out of Stagepass on their next request and can't book or sell until reactivated. Their tickets stay valid."
            confirmLabel="Suspend account"
            requireReason
            destructive
            onConfirm={async (reason) => {
              try {
                await suspend.mutateAsync({ id: u.id, reason })
                toast.success(`${name} suspended`)
              } catch (e) {
                fail(e, 'Could not suspend')
              }
            }}
          />
        </>
      )}
    </div>
  )
}

/** Find accounts, suspend them, and manage who is an admin. */
export function UsersPage() {
  const [q, setQ] = useState('')
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const me = useMe()
  const { data, isPending, isError } = useAdminUsers({ q, role, status, page })

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold">Users</h2>
        <p className="text-muted-foreground">Search by name or email. Organizer access comes from approving an application.</p>
      </div>
      <div className="flex flex-wrap gap-3">
        <SearchBox placeholder="Search name or email" onSearch={(v) => { setQ(v); setPage(0) }} />
        <StatusFilter value={role} options={ROLE_OPTIONS} onChange={(v) => { setRole(v); setPage(0) }} />
        <StatusFilter value={status} options={STATUS_OPTIONS} onChange={(v) => { setStatus(v); setPage(0) }} />
      </div>
      {isPending ? (
        <PageLoader />
      ) : isError ? (
        <EmptyState>We couldn’t load users. Refresh to try again.</EmptyState>
      ) : !data?.items.length ? (
        <EmptyState>No users match.</EmptyState>
      ) : (
        <>
          <AdminTable head={<><Th>User</Th><Th>Roles</Th><Th>Status</Th><Th>Joined</Th><Th right>Actions</Th></>}>
            {data.items.map((u) => (
              <tr key={u.id}>
                <Td>
                  <div className="font-medium">{u.fullName ?? '—'}</div>
                  <div className="text-xs text-muted-foreground">{u.email}</div>
                </Td>
                <Td className="text-xs">{u.roles.join(', ')}</Td>
                <Td>
                  {u.status === 'SUSPENDED' ? <StatusPill tone="bad">Suspended</StatusPill> : <StatusPill tone="good">Active</StatusPill>}
                  {u.suspensionReason && <div className="mt-1 max-w-56 text-xs text-muted-foreground">{u.suspensionReason}</div>}
                </Td>
                <Td className="whitespace-nowrap text-xs">{istDate.format(new Date(u.createdAt))}</Td>
                <Td right>
                  <UserActions u={u} isMe={me.data?.id === u.id} />
                </Td>
              </tr>
            ))}
          </AdminTable>
          <Pager page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} onPage={setPage} />
        </>
      )}
    </div>
  )
}
