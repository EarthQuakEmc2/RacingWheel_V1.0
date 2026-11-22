import { Navigate, Route, Routes } from 'react-router-dom'
import StartupSplash from './components/StartupSplash'
import Marketing from './Marketing'
import AccountPage from './pages/Account'
import LeaderboardPage from './pages/Leaderboard'
import Privacy from './pages/Privacy'
import SessionPage from './pages/Session'

export default function App() {
  return (
    <>
    <StartupSplash />
    <Routes>
      <Route path="/" element={<Marketing />} />
      <Route path="/privacy" element={<Privacy />} />
      <Route path="/account" element={<AccountPage />} />
      <Route path="/session/:id" element={<SessionPage />} />
      <Route path="/leaderboard" element={<LeaderboardPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
    </>
  )
}
