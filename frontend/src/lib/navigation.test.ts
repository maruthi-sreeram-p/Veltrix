import { describe, expect, it } from 'vitest';

import { dashboardKindFor, navigationFor, navigationTargets } from './navigation';
import type { SessionUser, UserRole } from './types';

/**
 * Navigation is decided from permissions, and getting it wrong is quiet: a user
 * offered the administrator's screens, or an administrator sent to a screen they
 * cannot use. None of that throws, so it has to be asserted.
 *
 * <p>The permission sets below are copied from the backend's UserRole enum. If
 * those ever diverge, these tests are where it should be noticed.
 */

function user(role: UserRole, permissions: string[]): SessionUser {
  return {
    id: 'user-1',
    email: 'person@example.com',
    fullName: 'Test Person',
    role,
    permissions,
    candidateId: null,
    onboardingStage: null,
  };
}

const USER = user('USER', [
  'SELF_PROFILE_MANAGE',
  'SELF_JOBS_MANAGE',
  'SELF_AI_USE',
  'SELF_ACCOUNT_DELETE',
]);

const ADMIN = user('ADMIN', [
  'SOURCE_VIEW',
  'SOURCE_MANAGE',
  'INGESTION_MANAGE',
  'SYSTEM_HEALTH_VIEW',
  'AUDIT_READ_PLATFORM',
  'AI_USAGE_MANAGE',
  'JOB_MARKET_VIEW',
]);

describe('which dashboard a person gets', () => {
  it('gives a user their own career dashboard', () => {
    expect(dashboardKindFor(USER)).toBe('user');
  });

  it('gives an administrator the platform dashboard', () => {
    expect(dashboardKindFor(ADMIN)).toBe('platform');
  });

  it('treats nobody signed in as a user, never as an administrator', () => {
    expect(dashboardKindFor(null)).toBe('user');
  });
});

describe('what each role is offered', () => {
  it('offers a user job discovery, saved jobs, alerts, profile and account', () => {
    const targets = navigationTargets(USER);
    expect(targets).toEqual(
      expect.arrayContaining([
        '/app',
        '/app/discover',
        '/app/saved',
        '/app/notifications',
        '/app/profile',
        '/app/account',
      ]),
    );
  });

  it('never offers a user any administrator screen', () => {
    const targets = navigationTargets(USER);
    expect(targets.some((to) => to.startsWith('/app/admin'))).toBe(false);
    expect(targets).not.toContain('/app/sources');
  });

  it('offers an administrator the source consoles', () => {
    const targets = navigationTargets(ADMIN);
    expect(targets).toEqual(
      expect.arrayContaining(['/app', '/app/admin/sources', '/app/admin/adapters', '/app/sources']),
    );
  });

  it('offers an administrator no profile or job-seeking screens', () => {
    const targets = navigationTargets(ADMIN);
    expect(targets).not.toContain('/app/profile');
    expect(targets).not.toContain('/app/saved');
  });

  it('offers nothing about colleges, students or placements to anybody', () => {
    for (const person of [USER, ADMIN]) {
      const everything = navigationTargets(person).join(' ');
      expect(everything).not.toMatch(/institution|students|placement|requirement/);
    }
  });

  it('puts the home link first and without a heading', () => {
    for (const person of [USER, ADMIN]) {
      const groups = navigationFor(person);
      expect(groups[0].label).toBeUndefined();
      expect(groups[0].items[0].to).toBe('/app');
    }
  });
});
