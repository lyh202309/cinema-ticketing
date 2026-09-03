// 主题预设：多套主色 + 明暗。通过覆盖 Element Plus 的 CSS 变量实现一键换肤。
export const THEMES = [
  { key: 'red',   label: '经典红', primary: '#e64d2e', dark: false },
  { key: 'green', label: '青柠绿', primary: '#00b578', dark: false },
  { key: 'amber', label: '琥珀金', primary: '#f59e0b', dark: false },
  { key: 'blue',  label: '黛青蓝', primary: '#3b6fff', dark: false },
  { key: 'night', label: '午夜',   primary: '#7c6cf0', dark: true },
]

export function getTheme(key) {
  return THEMES.find((t) => t.key === key) || THEMES[0]
}

// 应用主题：改写 --el-color-* 主色衍生 + 明暗(class=dark 由 element dark css-vars 接管组件)
export function applyTheme(key) {
  const t = getTheme(key)
  const root = document.documentElement
  root.style.setProperty('--el-color-primary', t.primary)
  root.style.setProperty('--el-color-primary-light-3', `color-mix(in srgb, ${t.primary} 70%, #fff)`)
  root.style.setProperty('--el-color-primary-light-5', `color-mix(in srgb, ${t.primary} 50%, #fff)`)
  root.style.setProperty('--el-color-primary-light-7', `color-mix(in srgb, ${t.primary} 30%, #fff)`)
  root.style.setProperty('--el-color-primary-light-8', `color-mix(in srgb, ${t.primary} 20%, #fff)`)
  root.style.setProperty('--el-color-primary-light-9', `color-mix(in srgb, ${t.primary} 10%, #fff)`)
  root.style.setProperty('--el-color-primary-dark-2', `color-mix(in srgb, ${t.primary} 80%, #000)`)
  root.classList.toggle('dark', t.dark)
}
