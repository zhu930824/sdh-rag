<template>
  <div class="table-toolbar">
    <div class="toolbar-left">
      <slot name="left" />
    </div>
    <div class="toolbar-right">
      <slot name="right" />
      <el-tooltip v-if="showRefresh" content="刷新" placement="top">
        <el-button :icon="Refresh" circle @click="$emit('refresh')" />
      </el-tooltip>
      <el-tooltip v-if="showColumnSetting" content="列设置" placement="top">
        <el-dropdown trigger="click" @command="handleColumnCommand">
          <el-button :icon="Setting" circle />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="col in columns"
                :key="col.prop"
                :command="col.prop"
              >
                <el-checkbox
                  :model-value="col.visible !== false"
                  @change="(val: boolean) => handleColumnChange(col.prop, val)"
                >
                  {{ col.label }}
                </el-checkbox>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-tooltip>
      <el-tooltip v-if="showDensity" content="密度" placement="top">
        <el-dropdown trigger="click" @command="handleDensityChange">
          <el-button :icon="Grid" circle />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="large" :class="{ active: density === 'large' }">
                宽松
              </el-dropdown-item>
              <el-dropdown-item command="default" :class="{ active: density === 'default' }">
                默认
              </el-dropdown-item>
              <el-dropdown-item command="small" :class="{ active: density === 'small' }">
                紧凑
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-tooltip>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Refresh, Setting, Grid } from '@element-plus/icons-vue'

/**
 * 列配置接口
 */
export interface ColumnConfig {
  prop: string
  label: string
  visible?: boolean
}

/**
 * 表格密度类型
 */
export type TableDensity = 'large' | 'default' | 'small'

interface Props {
  showRefresh?: boolean
  showColumnSetting?: boolean
  showDensity?: boolean
  columns?: ColumnConfig[]
  density?: TableDensity
}

interface Emits {
  (e: 'refresh'): void
  (e: 'column-change', prop: string, visible: boolean): void
  (e: 'density-change', density: TableDensity): void
}

withDefaults(defineProps<Props>(), {
  showRefresh: true,
  showColumnSetting: true,
  showDensity: true,
  columns: () => [],
  density: 'default',
})

const emit = defineEmits<Emits>()

// 处理列显示切换
function handleColumnChange(prop: string, visible: boolean): void {
  emit('column-change', prop, visible)
}

// 处理列命令（用于阻止事件冒泡）
function handleColumnCommand(): void {
  // 不执行任何操作，仅用于阻止下拉菜单关闭
}

// 处理密度切换
function handleDensityChange(density: TableDensity): void {
  emit('density-change', density)
}
</script>

<style scoped lang="scss">
.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .toolbar-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  :deep(.el-dropdown-menu__item.active) {
    color: var(--el-color-primary);
    background-color: var(--el-color-primary-light-9);
  }
}
</style>
