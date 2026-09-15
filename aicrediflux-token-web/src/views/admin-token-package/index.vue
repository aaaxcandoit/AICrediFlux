<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminTokenPackages, saveAdminTokenPackage } from '@/api/token-mall'
import type { TokenPackage } from '@/api/token-mall/types'

const rows = ref<TokenPackage[]>([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const form = reactive<Record<string, any>>({})
const text = (value: unknown) => value == null ? '' : String(value)

async function load() {
  loading.value = true
  try { rows.value = await getAdminTokenPackages() } finally { loading.value = false }
}
function edit(raw?: unknown) {
  const row = raw as TokenPackage | undefined
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, row || { status: 1, sort: 0, originalPrice: 0, salePrice: 0 })
  visible.value = true
}
async function save() {
  saving.value = true
  try {
    await saveAdminTokenPackage(form)
    ElMessage.success(form.id ? '套餐已更新' : '套餐已创建')
    visible.value = false
    await load()
  } finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div class="admin-page">
    <header><div><h1>AI Credit 套餐</h1><p>维护常规购买套餐；订单创建后使用价格与额度快照。</p></div><el-button type="primary" @click="edit()">新增套餐</el-button></header>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column label="额度" min-width="140"><template #default="{ row }">{{ text(row.creditAmount) }}</template></el-table-column>
      <el-table-column label="售价（分）" width="130"><template #default="{ row }">{{ text(row.salePrice) }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="90" align="right"><template #default="{ row }"><el-button text @click="edit(row)">编辑</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="form.id ? '编辑套餐' : '新增套餐'" width="520px">
      <el-form label-position="top">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" /></el-form-item>
        <div class="form-grid">
          <el-form-item label="额度"><el-input v-model="form.creditAmount" /></el-form-item>
          <el-form-item label="原价（分）"><el-input v-model="form.originalPrice" /></el-form-item>
          <el-form-item label="售价（分）"><el-input v-model="form.salePrice" /></el-form-item>
          <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<style scoped lang="scss">
.admin-page{display:grid;gap:20px;width:100%;max-width:1180px;margin:0 auto}header{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;padding:8px 0 20px;border-bottom:1px solid var(--el-border-color)}h1,p{margin:0}h1{font-size:26px}p{color:var(--el-text-color-secondary);font-size:13px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}@media(max-width:620px){header{align-items:flex-start;flex-direction:column}.form-grid{grid-template-columns:1fr}}
</style>