import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import basicSsl from '@vitejs/plugin-basic-ssl'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  // 加载环境变量
  const env = loadEnv(mode, process.cwd(), '')
  // 设为 0 可关闭 HTTPS（仅本机 http）；手机测摄像头建议保持默认以启用自签证书
  const devHttps = env.VITE_DEV_HTTPS !== '0'

  return {
  plugins: [vue(), ...(devHttps ? [basicSsl()] : [])],
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
    host: true,
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


