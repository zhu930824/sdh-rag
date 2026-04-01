<template>
  <div class="table-toolbar">
    <div class="toolbar-left">
      <slot name="left"></slot>
    </div>
    <div class="toolbar-right">
      <a-space>
        <a-dropdown>
          <a-button type="text" size="small">
            <template #icon>
              <ColumnHeightOutlined />
            </template>
          </a-button>
          <template #overlay>
            <a-menu>
              <a-menu-item @click="handleDensityChange('small')">
                <span :class="{ 'active-density': density === 'small' }">紧凑</span>
              </a-menu-item>
              <a-menu-item @click="handleDensityChange('default')">
                <span :class="{ 'active-density': density === 'default' }">默认</span>
              </a-menu-item>
              <a-menu-item @click="handleDensityChange('middle')">
                <span :class="{ 'active-density': density === 'middle' }">中等</span>
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <a-dropdown>
          <a-button type="text" size="small">
            <template #icon>
              <SettingOutlined />
            </template>
          </a-button>
          <template #overlay>
            <a-menu>
              <a-menu-item-group title="列设置">
                <a-menu-item v-for="col in columns" :key="col.prop">
                  <a-checkbox
                    :checked="col.visible !== false"
                    @change="handleColumnChange(col.prop, $event)"
                  >
                    {{ col.label }}
                  </a-checkbox>
                </a-menu-item>
              </a-menu-item-group>
            </a-menu>
          </template>
        </a-dropdown>
      </a-space>
    </div>
  </div>
</template>

<script setup lang="ts">
import { SettingOutlined, ColumnHeightOutlined } from '@ant-design/icons-vue'

export interface ColumnConfig {
  prop: string
  label: string
  visible?: boolean
}

export type TableDensity = 'small' | 'default' | 'middle'

interface Props {
  columns: ColumnConfig[]
  density?: TableDensity
}

const props = withDefaults(defineProps<Props>(), {
  density: 'default',
})

const emit = defineEmits<{
  columnChange: [prop: string, visible: boolean]
  densityChange: [density: TableDensity]
}>()

function handleColumnChange(prop: string, event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  emit('columnChange', prop, checked)
}

function handleDensityChange(density: TableDensity) {
  emit('densityChange', density)
}
</script>

<style scoped lang="scss">
.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-right {
  display: flex;
  align-items: center;
}

.active-density {
  color: var(--primary-color);
  font-weight: 500;
}
</style>
