import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'

export default tseslint.config(
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    ignores: ['dist/', 'node_modules/', 'coverage/'],
  },
  {
    rules: {
      'vue/multi-word-component-names': 'off',
    },
  }
)
