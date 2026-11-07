export type ScanResult = 'VALID' | 'ALREADY_USED' | 'INVALID' | 'WRONG_EVENT' | 'WRONG_GATE'

export interface ScanResponse {
  result: ScanResult
  message: string
  ticket?: { code: string; holderName: string; ticketType: string }
  firstScan?: { at: string; gateName: string | null; scannerName: string; byYou: boolean }
  allowedGates?: string[]
  counts: { checkedIn: number; total: number }
}

export interface GateOption {
  id: string
  name: string
  /** Empty = every ticket type. */
  ticketTypeNames: string[]
}

export interface ScannerEvent {
  id: string
  title: string
  startsAt: string
  endsAt: string
  venue: string
  organizerName: string
  owner: boolean
  gates: GateOption[]
}

export interface Gate {
  id: string
  name: string
  ticketTypeIds: string[]
  checkedIn: number
}

export interface CheckInStats {
  checkedIn: number
  total: number
  byGate: { gateId: string | null; name: string; checkedIn: number }[]
  byScanner: { name: string; checkedIn: number }[]
  lastScanAt: string | null
}

export interface ScanLogRow {
  at: string
  result: ScanResult
  ticketCode: string | null
  holderName: string | null
  gateName: string | null
  scannerName: string
  manual: boolean
}

/** Organizer-facing wording for each scan result. */
export const RESULT_LABEL: Record<ScanResult, string> = {
  VALID: 'Checked in',
  ALREADY_USED: 'Already used',
  WRONG_GATE: 'Wrong gate',
  WRONG_EVENT: 'Wrong event',
  INVALID: 'Not a ticket / invalid',
}
