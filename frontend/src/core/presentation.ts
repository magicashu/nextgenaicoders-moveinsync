export const diagnosticsEnabled = import.meta.env.VITE_SHOW_DIAGNOSTICS === 'true'
export const MANAGER_ROLES = { TRANSPORT_MANAGER: 'Transport manager', FACILITIES_HEAD: 'Transport & facilities head', LINE_MANAGER: 'Team / line manager' } as const
export type ManagerRole = keyof typeof MANAGER_ROLES
export function plain(text: string): string {
  return text.replaceAll('Delayed-trip rate', 'The share of late trips').replaceAll('delayed-trip rate', 'share of late trips')
    .replaceAll('prior four complete weeks', 'previous four weeks').replaceAll('prior four weeks', 'previous four weeks')
    .replaceAll('rider legs', 'passenger trip legs').replaceAll('attributable to', 'explained by')
    .replaceAll('Leg-level on-time pickups', 'On-time passenger pickups').replaceAll('Median billed cost per trip', 'The middle billed cost per trip')
}
export function plainCaveat(text: string): string {
  return plain(text).replace(/^(cost_per_km|feedback|severe_ack|gps_location|budget_variance|site_shift_direction):\s*/i, '')
}
export const scopeLabels: Record<string, string> = { businessUnit: 'Business unit', site: 'Site', site_id: 'Site', sites: 'Sites', shift: 'Shift', shift_id: 'Shift', shifts: 'Shifts', direction: 'Journey direction', watchDays: 'Monitoring period (days)', windowEnd: 'Period end', windowDays: 'Monitoring period (days)', durationDays: 'Monitoring period (days)', vendor_id: 'Vendor', vendor: 'Vendor', from: 'From', to: 'To', currentStart: 'Period start', currentEnd: 'Period end' }
export function labelForScope(key: string): string { return scopeLabels[key] ?? key.replace(/([a-z])([A-Z])/g, '$1 $2').replaceAll('_', ' ') }
