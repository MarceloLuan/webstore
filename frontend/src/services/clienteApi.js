import { getAuthHeader } from '@/services/auth'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

async function apiRequest(endpoint, options = {}) {
  const headers = {
    ...(options.headers || {}),
  }

  const authHeader = getAuthHeader()
  if (!options.skipAuth && authHeader && !headers.Authorization) {
    headers.Authorization = authHeader
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
  })

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

async function apiPost(endpoint, payload, options = {}) {
  return apiRequest(endpoint, {
    ...options,
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
}

export function loginCliente({ email, senha }) {
  return apiPost('/login', { email, senha }, { skipAuth: true })
}

export function cadastrarCliente({ nome, email, telefone, senha }) {
  return apiPost('/clientes', { nome, email, telefone, senha }, { skipAuth: true })
}

export function buscarMinhaConta() {
  return apiRequest('/minha-conta')
}

export function atualizarMinhaConta({ nome, email, telefone, senha }) {
  const payload = { nome, email, telefone }

  if (senha) {
    payload.senha = senha
  }

  return apiRequest('/minha-conta', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
}

export function deletarMinhaConta() {
  return apiRequest('/minha-conta', {
    method: 'DELETE',
  })
}
