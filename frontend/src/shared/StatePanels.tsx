import type { ApiError } from '../core/contracts'

export function LoadingPanel({ label }: { label: string }) {
  return (
    <section className="panel skeleton" role="status" aria-live="polite">
      <div className="skeleton-line wide" />
      <div className="skeleton-line" />
      <div className="skeleton-line narrow" />
      <p className="muted">{label}</p>
    </section>
  )
}

export function EmptyPanel({ title, body }: { title: string; body: string }) {
  return (
    <section className="panel empty">
      <p className="eyebrow">Nothing to show</p>
      <h3>{title}</h3>
      <p className="muted">{body}</p>
    </section>
  )
}

export function ErrorPanel({ status, error, message, onRetry }: { status?: number; error?: ApiError | null; message: string; onRetry?: () => void }) {
  const unavailable = status === 503 || error?.code === 'DEPENDENCY_UNAVAILABLE'
  const forbidden = status === 403
  return (
    <section className={`panel error-panel ${unavailable ? 'error-panel--unavailable' : ''}`} role="alert">
      <p className="eyebrow">{unavailable ? 'Dependency unavailable' : forbidden ? 'Not authorized' : 'Request failed'}</p>
      <h3>{unavailable ? 'The analytical plane is unavailable. No number was fabricated.' : forbidden ? 'This identity cannot access the requested tenant or action.' : message}</h3>
      {error && (
        <dl className="kv">
          <div><dt>Code</dt><dd>{error.code}</dd></div>
          <div><dt>Trace</dt><dd>{error.traceId}</dd></div>
          {error.details.length > 0 && <div><dt>Detail</dt><dd>{error.details.join('; ')}</dd></div>}
        </dl>
      )}
      {!error && <code>{message}</code>}
      {onRetry && <button type="button" className="ghost" onClick={onRetry}>Retry</button>}
    </section>
  )
}
