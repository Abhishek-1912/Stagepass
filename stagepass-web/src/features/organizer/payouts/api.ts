import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { PageResponse } from '@/lib/api/types'

export interface EventBalance {
  eventId: string
  title: string
  endsAt: string
  earnedPaise: number
  payableFrom: string | null
  payable: boolean
}

export interface Balances {
  pendingPaise: number
  payablePaise: number
  paidOutPaise: number
  balancePaise: number
  negative: boolean
  events: EventBalance[]
}

export interface LedgerLine {
  id: string
  type: 'SALE' | 'REFUND' | 'COMMISSION' | 'PAYOUT' | 'ADJUSTMENT' | 'TDS'
  amountPaise: number
  eventId: string | null
  eventTitle: string | null
  bookingCode: string | null
  note: string | null
  createdAt: string
}

export interface PayoutLine {
  id: string
  amountPaise: number
  reference: string
  paidAt: string
  note: string | null
}

export interface Ledger {
  balances: Balances
  entries: PageResponse<LedgerLine>
  payouts: PayoutLine[]
}

export function useLedger(page: number) {
  return useQuery({
    queryKey: ['organizer', 'ledger', page],
    queryFn: () => api<Ledger>(`/organizer/ledger?page=${page}&size=20`),
    placeholderData: keepPreviousData,
  })
}
