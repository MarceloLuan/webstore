export function setUser(user) {
  try {
    localStorage.setItem('user', JSON.stringify(user))
  } catch (e) {
    console.warn('Could not save user to localStorage', e)
  }
}

export function getUser() {
  try {
    const s = localStorage.getItem('user')
    return s ? JSON.parse(s) : null
  } catch (e) {
    console.warn('Could not read user from localStorage', e)
    return null
  }
}

export function clearUser() {
  try {
    localStorage.removeItem('user')
  } catch (e) {
    console.warn('Could not remove user from localStorage', e)
  }
}
