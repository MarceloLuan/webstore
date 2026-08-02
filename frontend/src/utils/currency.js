const brlFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
})

export function formatCurrency(value) {
  const amount = Number(value)
  return brlFormatter.format(Number.isFinite(amount) ? amount : 0)
}
