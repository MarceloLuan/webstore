const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

async function apiRequest(endpoint, options = {}) {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, options)

  const text = await response.text()
  let data = null

  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { message: text }
    }
  }

  if (!response.ok) {
    const message = data?.message || 'Nao foi possivel concluir a requisicao.'
    throw new Error(message)
  }

  return data
}

async function apiPost(endpoint, payload) {
  return apiRequest(endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
}

export function loginCliente({ email, senha }) {
  return apiPost('/login', { email, senha })
}

export function cadastrarCliente({ nome, email, telefone, senha }) {
  return apiPost('/clientes', { nome, email, telefone, senha })
}

export function listarClientes() {
  return apiRequest('/clientes')
}

export function buscarClientePorId(id) {
  return apiRequest(`/clientes/${id}`)
}

export function atualizarCliente(id, { nome, email, telefone, senha }) {
  const payload = { nome, email, telefone }

  if (senha) {
    payload.senha = senha
  }

  return apiRequest(`/clientes/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
}

export function deletarCliente(id) {
  return apiRequest(`/clientes/${id}`, {
    method: 'DELETE',
  })
}
