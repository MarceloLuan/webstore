import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import CadastroView from '@/views/CadastroView.vue'
import HomeView from '@/views/HomeView.vue'
import MinhaContaView from '@/views/MinhaContaView.vue'
import ProdutosCrudView from '@/views/ProdutosCrudView.vue'
import ProdutoDetalhesView from '@/views/ProdutoDetalhesView.vue'
import { getUser, isAuthenticated } from '@/services/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/home',
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
    },
    {
      path: '/cadastro',
      name: 'cadastro',
      component: CadastroView,
    },
    {
      path: '/home',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/minha-conta',
      name: 'minha-conta',
      component: MinhaContaView,
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/produtos',
      name: 'produtos',
      component: ProdutosCrudView,
      meta: {
        requiresAuth: true,
        roles: ['ADMIN'],
      },
    },
    {
      path: '/produtos/:id',
      name: 'produto-detalhes',
      component: ProdutoDetalhesView,
    },
  ],
})

router.beforeEach((to) => {
  const user = getUser()
  const authenticated = isAuthenticated()

  if (authenticated && (to.name === 'login' || to.name === 'cadastro')) {
    return { name: 'home' }
  }

  if (to.meta.requiresAuth && !authenticated) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
      },
    }
  }


  const allowedRoles = to.meta.roles
  if (allowedRoles?.length && !allowedRoles.includes(user?.role)) {
    return { name: 'home' }
  }

  return true
})

export default router
