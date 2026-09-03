<script setup>
import { THEMES } from '../theme'
import { useThemeStore } from '../stores/theme'

const themeStore = useThemeStore()
</script>

<template>
  <el-popover placement="bottom" :width="180" trigger="click">
    <template #reference>
      <el-button size="small">🎨 风格</el-button>
    </template>
    <div class="theme-list">
      <div
        v-for="t in THEMES"
        :key="t.key"
        class="theme-item"
        :class="{ active: themeStore.key === t.key }"
        @click="themeStore.setKey(t.key)"
      >
        <span class="swatch" :style="{ background: t.primary }" />
        <span class="label">{{ t.label }}</span>
        <span v-if="t.dark" class="badge">暗色</span>
      </div>
    </div>
  </el-popover>
</template>

<style scoped>
.theme-list { display: flex; flex-direction: column; gap: 4px; }
.theme-item { display: flex; align-items: center; gap: 10px; padding: 6px 8px;
  border-radius: 6px; cursor: pointer; }
.theme-item:hover { background: var(--el-fill-color-light); }
.theme-item.active { background: var(--el-color-primary-light-9); }
.swatch { width: 20px; height: 20px; border-radius: 50%; border: 1px solid rgba(0,0,0,.1); }
.label { font-size: 14px; }
.badge { font-size: 10px; color: #fff; background: #666; border-radius: 4px; padding: 1px 5px; }
</style>
