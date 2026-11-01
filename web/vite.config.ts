import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      dirs: [],
      dts: false,
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
    }),
  ],
  build: { manifest: true },
  server: {
    port: 5173,
    strictPort: true,
    proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true } },
  },
  test: {
    environment: 'jsdom',
    // 按需样式入口交给Vite处理，避免Node直接加载依赖中的CSS。
    server: {
      deps: {
        inline: [/element-plus[/\\]es[/\\]components[/\\].*[/\\]style[/\\]/],
      },
    },
    include: ['tests/**/*.test.ts'],
    restoreMocks: true,
    clearMocks: true,
  },
})
