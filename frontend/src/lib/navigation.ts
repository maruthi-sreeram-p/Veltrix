import type { SessionUser } from './types';
import { can } from './types';

/**
 * Which home screen a signed-in person gets, and what their sidebar contains.
 *
 * <p>This is deliberately plain data with no React in it. The navigation is the
 * part of the shell most likely to be got wrong in a way nobody notices — a user
 * quietly offered a link meant for the administrator — and keeping it as a pure
 * function means the rules can be asserted directly instead of by rendering a
 * tree and reading it back.
 *
 * Every decision is made from a permission rather than a role name. The server
 * re-checks all of it — this only decides what is worth offering.
 */

export type DashboardKind = 'user' | 'platform';

/** Icon keys, resolved to components by the shell. Kept as strings so this module stays pure. */
export type NavIcon =
  | 'Dashboard'
  | 'Compass'
  | 'Bookmark'
  | 'Document'
  | 'Bell'
  | 'User'
  | 'Sliders'
  | 'Radar'
  | 'Terminal'
  | 'Layers';

export interface NavItem {
  to: string;
  label: string;
  icon: NavIcon;
  /** Only match this route exactly, for entries whose path prefixes others. */
  end?: boolean;
}

export interface NavGroup {
  /** Omitted for the first group, which needs no heading above the home link. */
  label?: string;
  items: NavItem[];
}

export function dashboardKindFor(user: SessionUser | null): DashboardKind {
  if (can(user, 'SOURCE_MANAGE')) return 'platform';
  return 'user';
}

/** What this person's dashboard is called, in their own terms. */
export function dashboardTitle(kind: DashboardKind): string {
  switch (kind) {
    case 'platform':
      return 'Platform operations';
    default:
      return 'Your career';
  }
}

export function navigationFor(user: SessionUser | null): NavGroup[] {
  switch (dashboardKindFor(user)) {
    case 'platform':
      return platformNav();
    default:
      return userNav();
  }
}

/**
 * The job seeker's experience.
 *
 * <p>Applications and Preferences are not separate screens: applications are the
 * second tab of the saved list, and preferences the second tab of the profile.
 * Naming them here gives the two things a user actually asks for their own entry
 * without inventing a page to hold them.
 */
function userNav(): NavGroup[] {
  return [
    { items: [{ to: '/app', label: 'Dashboard', icon: 'Dashboard', end: true }] },
    {
      label: 'Find work',
      items: [
        { to: '/app/discover', label: 'Discover', icon: 'Compass' },
        { to: '/app/saved', label: 'Saved', icon: 'Bookmark', end: true },
        { to: '/app/saved?tab=applied', label: 'Applications', icon: 'Document' },
      ],
    },
    {
      label: 'You',
      items: [
        { to: '/app/notifications', label: 'Alerts', icon: 'Bell' },
        { to: '/app/profile', label: 'Career profile', icon: 'User', end: true },
        { to: '/app/profile?tab=Preferences', label: 'Preferences', icon: 'Sliders' },
        { to: '/app/account', label: 'Account', icon: 'User' },
      ],
    },
  ];
}

function platformNav(): NavGroup[] {
  return [
    { items: [{ to: '/app', label: 'Overview', icon: 'Terminal', end: true }] },
    {
      label: 'Job sources',
      items: [
        { to: '/app/admin/sources', label: 'Source list', icon: 'Sliders' },
        { to: '/app/admin/adapters', label: 'Source types', icon: 'Layers' },
        { to: '/app/sources', label: 'Job sources', icon: 'Radar' },
      ],
    },
    {
      label: 'You',
      items: [
        { to: '/app/notifications', label: 'Alerts', icon: 'Bell' },
        { to: '/app/account', label: 'Account', icon: 'User' },
      ],
    },
  ];
}

/** Every destination a role is offered, for assertions and for route guarding. */
export function navigationTargets(user: SessionUser | null): string[] {
  return navigationFor(user).flatMap((group) => group.items.map((item) => item.to));
}
