import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import FriendsPage from './pages/FriendsPage';
import ReceiptsPage from './pages/ReceiptsPage';
import ReceiptDetailPage from './pages/ReceiptDetailPage';
import DebtDetailPage from './pages/DebtDetailPage';
import NotificationsPage from './pages/NotificationsPage';
import ProfilePage from './pages/ProfilePage';
import ChatPage from './pages/ChatPage';
import AdminPage from './pages/AdminPage';
import { ReactNode } from 'react';

function Protected({ children }: { children: ReactNode }) {
  const { profile, loading } = useAuth();
  if (loading)
    return (
      <div className="min-h-screen bg-surface text-on-surface-variant flex items-center justify-center">
        Loading…
      </div>
    );
  if (!profile) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function AdminOnly({ children }: { children: ReactNode }) {
  const { profile, loading } = useAuth();
  if (loading) return null;
  if (!profile) return <Navigate to="/login" replace />;
  if (profile.role !== 'ADMIN') return <Navigate to="/" replace />;
  return <>{children}</>;
}

/** Admins see the admin dashboard at "/"; regular users see the normal dashboard. */
function HomeByRole() {
  const { profile } = useAuth();
  if (profile?.role === 'ADMIN') return <Navigate to="/admin" replace />;
  return <DashboardPage />;
}

/** User-facing pages: admins are redirected to their dashboard. */
function UserOnly({ children }: { children: ReactNode }) {
  const { profile, loading } = useAuth();
  if (loading) return null;
  if (!profile) return <Navigate to="/login" replace />;
  if (profile.role === 'ADMIN') return <Navigate to="/admin" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route
        element={
          <Protected>
            <Layout />
          </Protected>
        }
      >
        <Route path="/" element={<HomeByRole />} />
        <Route path="/friends" element={<UserOnly><FriendsPage /></UserOnly>} />
        <Route path="/receipts" element={<UserOnly><ReceiptsPage /></UserOnly>} />
        <Route path="/receipts/:id" element={<UserOnly><ReceiptDetailPage /></UserOnly>} />
        <Route path="/debts/:id" element={<UserOnly><DebtDetailPage /></UserOnly>} />
        <Route path="/messages/:username" element={<UserOnly><ChatPage /></UserOnly>} />
        <Route path="/notifications" element={<UserOnly><NotificationsPage /></UserOnly>} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/admin" element={<AdminOnly><AdminPage /></AdminOnly>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
