<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminCampaigns,
  getAdminTokenPackages,
  publishAdminCampaign,
  reconcileAdminCampaign,
  saveAdminCampaign
} from '@/api/token-mall'
import type { FlashSaleCampaign, TokenPackage } from '@/api/token-mall/types'

const rows = ref<FlashSaleCampaign[]>([])
const packages = ref<TokenPackage[]>([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const form = reactive<Record<string, any>>({})
const range = ref<[Date, Date] | null>(null)
const statusText = (status: number) => status === 0 ? '草稿' : status === 1 ? '已发布' : '待对账'
const statusType = (status: number) => status === 0 ? 'info' : status === 1 ? 'success' : 'warning'

async function load() {
  loading.value = true
  try {
    ;[rows.value, packages.value] = await Promise.all([getAdminCampaigns(), getAdminTokenPackages()])
  } finally { loading.value = false }
}
function edit(raw?: unknown) {
  const row = raw as FlashSaleCampaign | undefined
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, row || { totalStock: 100, flashPrice: 0, creditAmount: 0 })
  range.value = row ? [new Date(row.startTime), new Date(row.endTime)] : null
  visible.value = true
}
async function save() {
  if (!range.value) return ElMessage.warning('请选择活动时间')
  form.startTime = range.value[0].toISOString()
  form.endTime = range.value[1].toISOString()
  saving.value = true
  try {
    await saveAdminCampaign(form)
    ElMessage.success(form.id ? '活动已更新' : '活动已创建')
    visible.value = false
    await load()
  } finally { saving.value = false }
}
async function publish(raw: unknown) {
  const row = raw as FlashSaleCampaign
  await ElMessageBox.confirm('发布后价格、额度和总库存不可修改，确认发布？', '发布活动', { type: 'warning' })
  await publishAdminCampaign(row.id)
  ElMessage.success('活动已发布并完成 Redis 预热')
  await load()
}
async function reconcile(raw: unknown) {
  const row = raw as FlashSaleCampaign
  await reconcileAdminCampaign(row.id)
  ElMessage.success('Redis 库存与用户资格已按数据库重建')
  await load()
}
onMounted(load)
</script>

<template>
  <div class="admin-page">
    <header><div><h1>秒杀活动</h1><p>活动发布后冻结交易快照；Redis 状态异常时先暂停再对账。</p></div><el-button type="primary" @click="edit()">新增活动</el-button></header>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="activityName" label="活动" min-width="180" />
      <el-table-column label="库存" width="120"><template #default="{ row }">{{ row.availableStock }} / {{ row.totalStock }}</template></el-table-column>
      <el-table-column prop="creditAmount" label="额度" min-width="130" />
      <el-table-column prop="flashPrice" label="活动价（分）" min-width="130" />
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="230" align="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" text @click="edit(row)">编辑</el-button>
          <el-button v-if="row.status === 0" type="primary" text @click="publish(row)">发布</el-button>
          <el-button v-if="row.status === 2" type="warning" text @click="reconcile(row)">对账恢复</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="form.id ? '编辑活动' : '新增活动'" width="560px">
      <el-form label-position="top">
        <el-form-item label="活动名称"><el-input v-model="form.activityName" /></el-form-item>
        <el-form-item label="AI Credit 套餐">
          <el-select v-model="form.packageId" style="width:100%">
            <el-option v-for="item in packages" :key="item.id.toString()" :label="item.name" :value="String(item.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动时间">
          <el-date-picker v-model="range" type="datetimerange" start-placeholder="开始时间" end-placeholder="结束时间" style="width:100%" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="活动额度"><el-input v-model="form.creditAmount" /></el-form-item>
          <el-form-item label="活动价（分）"><el-input v-model="form.flashPrice" /></el-form-item>
          <el-form-item label="总库存"><el-input-number v-model="form.totalStock" :min="1" controls-position="right" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<style scoped lang="scss">
.admin-page{display:grid;gap:20px;width:100%;max-width:1180px;margin:0 auto}header{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;padding:8px 0 20px;border-bottom:1px solid var(--el-border-color)}h1,p{margin:0}h1{font-size:26px}p{color:var(--el-text-color-secondary);font-size:13px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}@media(max-width:620px){header{align-items:flex-start;flex-direction:column}.form-grid{grid-template-columns:1fr}}
</style>