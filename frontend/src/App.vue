<script setup>
import { RouterView } from 'vue-router'
import logo from '@/assets/logo.png'
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <div class="brand-block">
        <img :src="logo" alt="Logo da WebStore" class="brand-logo" />
      </div>

      <p class="brand-message">O Melhor da Moda Feminina</p>

      <div class="brand-block" aria-hidden="true"></div>
    </header>

    <main class="content-area">
      <RouterView />
    </main>
  </div>
</template>

<style>
:root {
  --bg-beige: #f3e8df;
  --card-white: #ffffff;
  --primary-wine: #6a1b2c;
  --input-gray: #f2efed;
  --text-dark: #4b3a3a;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  min-height: 100vh;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  background:
    radial-gradient(circle at top right, #f6ede7 0%, #f3e8df 58%),
    radial-gradient(circle at bottom left, #f1e3d8 0%, #f3e8df 60%);
  color: var(--text-dark);
}

#app {
  min-height: 100vh;
}

.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.site-header {
  width: min(1080px, calc(100% - 2rem));
  margin: 1.25rem auto 0;
  border-radius: 18px;
  background: #efe1d6;
  border-bottom: 3px solid var(--primary-wine);
  padding: 0.9rem 1.1rem;
  display: grid;
  grid-template-columns: 130px 1fr 130px;
  align-items: center;
  gap: 1rem;
}

.brand-block {
  height: 52px;
  display: flex;
  align-items: center;
}

.brand-logo {
  max-height: 100%;
  max-width: 120px;
  object-fit: contain;
}

.brand-message {
  margin: 0;
  text-align: center;
  color: var(--primary-wine);
  font-weight: 700;
  letter-spacing: 0.03em;
}

.content-area {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 2rem 1rem 2.4rem;
}

@media (max-width: 700px) {
  .site-header {
    width: calc(100% - 1rem);
    grid-template-columns: 86px 1fr 86px;
    padding: 0.7rem 0.75rem;
  }

  .brand-message {
    font-size: 0.92rem;
  }

  .content-area {
    padding: 1.4rem 0.7rem 1.8rem;
    align-items: flex-start;
  }
}
</style>
