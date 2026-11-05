import { useAuth } from '../lib/auth';
import { dashboardKindFor } from '../lib/navigation';
import PlatformAdminDashboard from './dashboards/PlatformAdminDashboard';
import UserDashboard from './dashboards/UserDashboard';

/**
 * Resolves the home screen to the one this person's job needs.
 *
 * <p>Everyone keeps the same URL, {@code /app}. The choice is made from
 * permissions rather than role names, and the server decides independently what
 * each of these screens is allowed to load. This is a routing decision, not an
 * authorization one.
 */
export default function Dashboard() {
  const { user } = useAuth();

  switch (dashboardKindFor(user)) {
    case 'platform':
      return <PlatformAdminDashboard />;
    default:
      return <UserDashboard />;
  }
}
