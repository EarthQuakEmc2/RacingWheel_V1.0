import { Navigate, Route, Routes } from 'react-router-dom'
import Marketing from './Marketing'
import AccountPage from './pages/Account'
import BundlePickerPage from './pages/BundlePicker'
import CartPage from './pages/Cart'
import CheckoutPage from './pages/Checkout'
import LeaderboardPage from './pages/Leaderboard'
import PackageDetailPage from './pages/PackageDetail'
import PackagesPage from './pages/Packages'
import Privacy from './pages/Privacy'
import SessionPage from './pages/Session'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Marketing />} />
      <Route path="/privacy" element={<Privacy />} />
      <Route path="/account" element={<AccountPage />} />
      <Route path="/packages" element={<PackagesPage />} />
      <Route path="/packages/bundle/:id" element={<BundlePickerPage />} />
      <Route path="/packages/:id" element={<PackageDetailPage />} />
      <Route path="/cart" element={<CartPage />} />
      <Route path="/checkout" element={<CheckoutPage />} />
      <Route path="/session/:id" element={<SessionPage />} />
      <Route path="/leaderboard" element={<LeaderboardPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
