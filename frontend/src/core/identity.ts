import type { Identity } from './contracts'

export const TENANTS = ['pinnacle-Slc', 'vanta-Sea', 'vanta-Aus', 'catalyst-Sac', 'orbit-Slc'] as const
export const ROLES = ['TRANSPORT_MANAGER', 'FACILITIES_HEAD', 'LINE_MANAGER'] as const

export const defaultIdentity: Identity = {
  actorId: 'transport-manager-demo',
  businessUnit: 'pinnacle-Slc',
  roles: ['TRANSPORT_MANAGER'],
}

/** Headers the trusted edge would inject. Body text never carries tenant or role. */
export function identityHeaders(identity: Identity): Record<string, string> {
  return {
    'X-Actor-Id': identity.actorId,
    'X-Business-Unit': identity.businessUnit,
    'X-Roles': identity.roles.join(','),
  }
}
