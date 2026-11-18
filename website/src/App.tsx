import { Navigate, Route, Routes } from 'react-router-dom'
import Marketing from './Marketing'
import AccountPage from './pages/Account'
import LeaderboardPage from './pages/Leaderboard'
import SessionPage from './pages/Session'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Marketing />} />
      <Route path="/account" element={<AccountPage />} />
      <Route path="/session/:id" element={<SessionPage />} />
      <Route path="/leaderboard" element={<LeaderboardPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
