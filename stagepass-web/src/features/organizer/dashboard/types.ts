export interface DayPoint {
  /** IST calendar date, `yyyy-mm-dd`. */
  date: string
  tickets: number
  amountPaise: number
}

export type Range = 7 | 30 | 90

export interface RecentBooking {
  id: string
  eventId: string
  code: string
  eventTitle: string
  attendeeName: string
  ticketCount: number
  totalPaise: number
  confirmedAt: string
}

export interface Overview {
  liveEvents: number
  upcomingEvents: number
  ticketsSold: number
  grossSalesPaise: number
  netSalesPaise: number
  earningsPaise: number
  recentBookings: RecentBooking[]
  dailySales: DayPoint[]
}

export interface TypeStats {
  ticketTypeId: string
  name: string
  pricePaise: number
  total: number
  sold: number
  held: number
  netSalesPaise: number
}

export interface EventStats {
  capacity: number
  ticketsSold: number
  held: number
  checkedIn: number
  grossSalesPaise: number
  refundedPaise: number
  netSalesPaise: number
  commissionPaise: number
  byType: TypeStats[]
  dailySales: DayPoint[]
}
