import { getAuthHeader } from '@/services/auth'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

async function apiRequest(endpoint, options = {}) {
  const headers = {
    ...options.headers,
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
    const message =
      data?.message ||
      data?.detail ||
      data?.title ||
      data?.error ||
      'Não foi possível concluir a requisição.'
    const error = new Error(message)
    error.status = response.status
    throw error
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

export function cadastrarCliente({ nome, email, telefone, senha, confirmacaoSenha }) {
  return apiPost('/clientes', { nome, email, telefone, senha, confirmacaoSenha }, { skipAuth: true })
}

export function buscarMinhaConta() {
  return apiRequest('/minha-conta')
}

export function atualizarMinhaConta({ nome, email, telefone, senhaAtual, senha, confirmacaoSenha }) {
  const payload = { nome, email, telefone }

  if (senha) {
    payload.senhaAtual = senhaAtual
    payload.senha = senha
    payload.confirmacaoSenha = confirmacaoSenha
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

export function listarProdutos() {
  return apiRequest('/produtos')
}

export function listarProdutoOpcoes() {
  return apiRequest('/produtos/opcoes')
}

export function listarProdutosAdmin() {
  return apiRequest('/admin/produtos')
}

export function criarProduto(payload) {
  return apiPost('/admin/produtos', payload)
}

export function atualizarProduto(id, payload) {
  return apiRequest(`/admin/produtos/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
}

export function excluirProduto(id) {
  return apiRequest(`/admin/produtos/${id}`, {
    method: 'DELETE',
  })
}

