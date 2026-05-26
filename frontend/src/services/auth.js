const USER_KEY = 'user'
const AUTH_TOKEN_KEY = 'authToken'

export function setUser(user) {
  try {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  } catch (e) {
    console.warn('Could not save user to localStorage', e)
  }
}

export function getUser() {
  try {
    const s = localStorage.getItem(USER_KEY)
    return s ? JSON.parse(s) : null
  } catch (e) {
    console.warn('Could not read user from localStorage', e)
    return null
  }
}

export function setAuthToken(token) {
  try {
    localStorage.setItem(AUTH_TOKEN_KEY, token)
  } catch (e) {
    console.warn('Could not save auth token to localStorage', e)
  }
}

export function setAuthCredentials(token) {
  setAuthToken(token)
}

export function getAuthToken() {
  try {
    return localStorage.getItem(AUTH_TOKEN_KEY)
  } catch (e) {
    console.warn('Could not read auth token from localStorage', e)
    return null
  }
}

export function getAuthHeader() {
  const token = getAuthToken()
  return token ? `Bearer ${token}` : ''
}

export function isAuthenticated() {
  return Boolean(getUser() && getAuthToken())
}

export function clearUser() {
  try {
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(AUTH_TOKEN_KEY)
  } catch (e) {
    console.warn('Could not remove auth data from localStorage', e)
  }
}
