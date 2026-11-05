export interface FieldError {
  field: string
  message: string
}

/** A failed API call, normalised from the server's RFC 9457 Problem Details body. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly detail: string
  readonly fieldErrors: FieldError[]

  constructor(status: number, code: string, detail: string, fieldErrors: FieldError[] = []) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.detail = detail
    this.fieldErrors = fieldErrors
  }

  static fromProblem(status: number, body: unknown): ApiError {
    const problem = (body && typeof body === 'object' ? body : {}) as Record<string, unknown>
    const code = typeof problem.code === 'string' ? problem.code : status >= 500 ? 'INTERNAL' : 'BAD_REQUEST'
    const detail =
      typeof problem.detail === 'string' && problem.detail
        ? problem.detail
        : status >= 500
          ? 'Something went wrong on our side. Please try again.'
          : 'The request could not be completed.'
    const errors = Array.isArray(problem.errors) ? (problem.errors as FieldError[]) : []
    return new ApiError(status, code, detail, errors)
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
