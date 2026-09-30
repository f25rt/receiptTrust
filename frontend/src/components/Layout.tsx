import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function Layout() {
  const { profile, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">ReceiptTrust</div>
        <nav className="nav">
          <NavLink to="/" end>Dashboard</NavLink>
          <NavLink to="/friends">Friends</NavLink>
          <NavLink to="/receipts">Receipts</NavLink>
          <NavLink to="/notifications">Notifications</NavLink>
          <NavLink to="/profile">Profile</NavLink>
        </nav>
        <div className="user-chip">
          <span className="score-badge" title="Trust score">{profile?.trustScore}</span>
          <span>{profile?.username}</span>
          <button className="btn-ghost" onClick={handleLogout}>Log out</button>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
