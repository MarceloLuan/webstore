let loading

export function carregarGoogleIdentity() {
  if (window.google?.accounts?.id) return Promise.resolve(window.google.accounts.id)
  if (loading) return loading
  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    const fail = () => {
      clearTimeout(timeout)
      script.remove()
      loading = undefined
      reject(new Error('Não foi possível carregar o Google. Você pode entrar com e-mail e senha ou tentar novamente.'))
    }
    const timeout = setTimeout(fail, 15000)
    script.src = 'https://accounts.google.com/gsi/client'
    script.async = true
    script.onload = () => {
      if (!window.google?.accounts?.id) return fail()
      clearTimeout(timeout)
      resolve(window.google.accounts.id)
    }
    script.onerror = fail
    document.head.appendChild(script)
  })
  return loading
}
