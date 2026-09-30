import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Avatar, Icon } from './ui';
import { trustTier } from '../lib/format';

const NAV = [
  { to: '/', icon: 'dashboard', label: 'Dashboard', end: true },
  { to: '/friends', icon: 'group', label: 'Friends & Peers' },
  { to: '/receipts', icon: 'receipt_long', label: 'Receipts & Splits' },
  { to: '/notifications', icon: 'notifications', label: 'Activity & Audit' },
  { to: '/profile', icon: 'person', label: 'Profile' },
];

export default function Layout() {
  const { profile, logout } = useAuth();
  const navigate = useNavigate();
  const tier = trustTier(profile?.trustScore ?? 500);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const trustPill = (
    <button
      onClick={() => navigate('/profile')}
      className={`flex items-center gap-space-2xs ${tier.bg} px-space-sm py-space-2xs rounded-full`}
    >
      <span className={`font-label-md ${tier.color}`}>{profile?.trustScore ?? '—'}</span>
      <span className="text-[11px] leading-none">⭐ {tier.label}</span>
    </button>
  );

  return (
    <div className="min-h-screen bg-surface text-on-surface">
      {/* ---- Mobile top bar (below lg) ---- */}
      <header className="lg:hidden fixed top-0 w-full z-50 pt-safe bg-surface/80 backdrop-blur-xl border-b border-white/[0.06]">
        <div className="max-w-md mx-auto h-16 px-margin flex items-center justify-between gap-space-sm">
          <button className="flex items-center gap-space-sm min-w-0" onClick={() => navigate('/')}>
            <div className="h-8 w-8 rounded-lg bg-primary-container/20 flex items-center justify-center text-primary shrink-0">
              <Icon name="verified_user" className="text-[20px]" />
            </div>
            <span className="font-headline-sm text-on-surface tracking-tight truncate leading-tight">
              ReceiptTrust
            </span>
          </button>
          <div className="flex items-center gap-space-sm shrink-0">
            {trustPill}
            <button onClick={() => navigate('/profile')} className="relative">
              <Avatar name={profile?.fullName ?? '?'} size={32} imagePath={profile?.profileImagePath ?? null} />
              <span className="absolute top-0 right-0 w-2.5 h-2.5 rounded-full bg-tertiary ring-2 ring-surface" />
            </button>
          </div>
        </div>
      </header>

      {/* ---- Desktop top nav (lg+) ---- */}
      <header className="hidden lg:block fixed top-0 w-full z-50 bg-surface/80 backdrop-blur-xl border-b border-white/[0.06]">
        <div className="max-w-6xl mx-auto h-16 px-space-lg flex items-center gap-space-lg">
          <button className="flex items-center gap-space-sm shrink-0" onClick={() => navigate('/')}>
            <div className="h-8 w-8 rounded-lg bg-primary-container/20 flex items-center justify-center text-primary">
              <Icon name="verified_user" className="text-[20px]" />
            </div>
            <span className="font-headline-sm text-on-surface tracking-tight">ReceiptTrust</span>
          </button>

          <nav className="flex items-center gap-space-xs flex-1">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `px-space-md py-2 rounded-lg font-label-md transition-colors ${
                    isActive
                      ? 'bg-surface-container-high text-on-surface'
                      : 'text-on-surface-variant hover:text-on-surface'
                  }`
                }
              >
                {item.label.replace(' & Peers', '').replace(' & Splits', '').replace(' & Audit', '')}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-space-sm shrink-0">
            {trustPill}
            <button
              onClick={() => navigate('/notifications')}
              className="w-9 h-9 rounded-full bg-surface-container-high flex items-center justify-center text-on-surface-variant active:scale-95 transition-transform relative"
              aria-label="Activity"
            >
              <Icon name="notifications" className="text-[18px]" />
            </button>
            <button onClick={() => navigate('/profile')} className="flex items-center gap-space-2xs">
              <Avatar name={profile?.fullName ?? '?'} size={32} imagePath={profile?.profileImagePath ?? null} />
            </button>
            <button
              aria-label="Log out"
              onClick={handleLogout}
              className="w-9 h-9 rounded-full bg-surface-container-high flex items-center justify-center text-on-surface-variant active:scale-95 transition-transform"
            >
              <Icon name="logout" className="text-[18px]" />
            </button>
          </div>
        </div>
      </header>

      {/* ---- Content ---- */}
      <main className="relative w-full min-h-screen pt-16 pb-28 lg:pb-space-2xl">
        <div className="max-w-md lg:max-w-6xl mx-auto lg:px-space-lg">
          <Outlet />
        </div>
        {/* Desktop footer */}
        <footer className="hidden lg:block max-w-6xl mx-auto px-space-lg py-space-lg mt-space-lg border-t border-white/[0.06]">
          <div className="flex items-center justify-between font-label-sm text-on-surface-variant">
            <span className="flex items-center gap-space-2xs">
              <Icon name="verified_user" className="text-[14px] text-primary" />
              ReceiptTrust · Receipt-backed settlement network
            </span>
            <span>© {new Date().getFullYear()} ReceiptTrust</span>
          </div>
        </footer>
      </main>

      {/* ---- Glass bottom nav (below lg) ---- */}
      <nav className="lg:hidden fixed bottom-0 w-full z-50 pb-safe bg-surface-container-lowest/85 backdrop-blur-xl border-t border-white/[0.08] shadow-[0_-4px_20px_rgba(0,0,0,0.45)]">
        <div className="max-w-md mx-auto h-16 px-space-xs flex items-center justify-around relative">
          <MobileNavItem to="/" icon="dashboard" label="Dashboard" end />
          <MobileNavItem to="/friends" icon="group" label="Friends" />
          <div className="relative -top-3 flex flex-col items-center justify-center">
            <button
              onClick={() => navigate('/receipts')}
              aria-label="New receipt"
              className="w-14 h-14 rounded-full bg-primary-container flex items-center justify-center text-on-primary shadow-[0_0_20px_rgba(77,142,255,0.4)] active:scale-95 transition-transform"
            >
              <Icon name="photo_camera" className="text-[28px]" />
            </button>
            <span className="font-label-sm text-on-surface-variant mt-1">Scan</span>
          </div>
          <MobileNavItem to="/notifications" icon="notifications" label="Activity" />
          <MobileNavItem to="/profile" icon="person" label="Profile" />
        </div>
      </nav>
    </div>
  );
}

function MobileNavItem({
  to,
  icon,
  label,
  end = false,
}: {
  to: string;
  icon: string;
  label: string;
  end?: boolean;
}) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        `flex flex-col items-center justify-center min-w-[56px] min-h-[44px] gap-space-2xs transition-colors ${
          isActive ? 'text-primary' : 'text-on-surface-variant hover:text-on-surface'
        }`
      }
    >
      <Icon name={icon} className="text-[24px]" />
      <span className="font-label-sm">{label}</span>
    </NavLink>
  );
}
