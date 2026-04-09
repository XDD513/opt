import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  // 加载环境变量
  const env = loadEnv(mode, process.cwd(), '')
  
  return {
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        // 使用新的 Sass API（Sass 1.80+ 支持）
        // 这将消除 legacy-js-api 警告
        api: 'modern-compiler',
        // 如果上面的配置不工作，可以使用以下方式静默警告
        // silenceDeprecations: ['legacy-js-api']
      }
    }
  },
  server: {
    port: parseInt(env.VITE_PORT || '3000', 10),
    proxy: {
      '/api': {
        target: env.VITE_API_BASE_URL || 'http://localhost:8000',
        changeOrigin: true
      },
      '/ws': {
        target: env.VITE_WS_BASE_URL || env.VITE_API_BASE_URL || 'http://localhost:8000',
        changeOrigin: true,
        ws: true,
        secure: false
      }
    }
  }
  }
})


