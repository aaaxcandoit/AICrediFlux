<script setup lang="ts">
import { computed, onActivated, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getSubmissionStatus,
  getTokenOrders,
  mockPayTokenOrder
} from '@/api/token-mall'
import type { IntegerValue, SubmissionStatus, TokenOrder } from '@/api/token-mall/types'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const paying = ref('')
const orders = ref<TokenOrder[]>([])
const pending = ref<SubmissionStatus | null>(null)
const pendingOrderNo = computed(() => String(route.query.orderNo || ''))
let pollTimer: number | undefined

const numberText = (value: IntegerValue) => value.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
const money = (value: IntegerValue) => '¥' + (Number(value) / 100).toFixed(2)
const dateTime = (value?: number | string) => value ? new Date(value).toLocaleString() : '-'
const statusType = (raw: unknown) => {
  const order = raw as TokenOrder
  if (order.creditStatus === 'GRANTED') return 'success'
  if (order.orderStatus === 'CLOSED') return 'info'
  if (order.payStatus === 'PAID') return 'warning'
  return 'primary'
}
const statusText = (raw: unknown) => {
  const order = raw as TokenOrder
  if (order.creditStatus === 'GRANTED') return 'AI Credit 已到账'
  if (order.orderStatus === 'CLOSED') return '已关闭'
  if (order.payStatus === 'PAID') return '支付成功，AI Credit 发放中'
  return '待支付'
}

async function load() {
  loading.value = true
  try {
    orders.value = await getTokenOrders()
  } finally {
    loading.value = false
  }
}

async function checkPending() {
  if (!pendingOrderNo.value) return
  try {
    pending.value = await getSubmissionStatus(pendingOrderNo.value)
    if (pending.value.status === 'SUCCESS') {
      window.clearInterval(pollTimer)
      await load()
    } else if (pending.value.status === 'FAILED') {
      window.clearInterval(pollTimer)
    }
  } catch {
    pending.value = { status: 'PENDING' }
  }
}

async function pay(raw: unknown) {
  const order = raw as TokenOrder
  paying.value = order.orderNo
  try {
    await mockPayTokenOrder(order.orderNo)
    ElMessage.success('支付成功，AI Credit 正在发放到钱包')
    await load()
  } finally {
    paying.value = ''
  }
}

function startPolling() {
  window.clearInterval(pollTimer)
  if (!pendingOrderNo.value) return
  checkPending()
  pollTimer = window.setInterval(checkPending, 1500)
}

onMounted(() => { load(); startPolling() })
onActivated(() => { load(); startPolling() })
onUnmounted(() => window.clearInterval(pollTimer))
</script>

<template>
  <div class="orders-page">
    <header class="page-head">
      <div>
        <h1>AI Credit 订单</h1>
        <p>支付成功后，AI Credit 会异步发放到钱包余额，可安全刷新页面。</p>
      </div>
      <div class="actions">
        <el-button circle title="刷新" aria-label="刷新" @click="load"><i class="i-ep-refresh" /></el-button>
        <el-button type="primary" @click="router.push('/token-mall')">购买 AI Credit</el-button>
      </div>
    </header>

    <el-alert
      v-if="pendingOrderNo && pending?.status === 'PENDING'"
      type="info"
      :closable="false"
      show-icon
      title="抢购请求处理中"
      :description="'订单号 ' + pendingOrderNo + '，系统正在确认库存并创建订单。'"
    />
    <el-alert
      v-else-if="pendingOrderNo && pending?.status === 'FAILED'"
      type="error"
      :closable="false"
      show-icon
      title="抢购未成功"
      :description="pending.reason || '请求未能创建订单，Redis 库存与购买资格已补偿。'"
    />

    <el-table v-loading="loading" :data="orders" stripe>
      <el-table-column prop="orderNo" label="订单号" min-width="190" />
      <el-table-column label="套餐" min-width="170">
        <template #default="{ row }">
          <strong>{{ row.packageName }}</strong>
          <div class="muted">{{ row.orderSource === 'FLASH_SALE' ? '限时抢购' : '常规购买' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="到账 AI Credit" min-width="150">
        <template #default="{ row }">{{ numberText(row.creditAmount) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="110">
        <template #default="{ row }">{{ money(row.payAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" min-width="170">
        <template #default="{ row }">
          <el-tag :type="statusType(row)" effect="plain">{{ statusText(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="170">
        <template #default="{ row }">{{ dateTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" align="right">
        <template #default="{ row }">
          <el-button
            v-if="row.orderStatus === 'CREATED' && row.payStatus === 'UNPAID'"
            type="primary"
            :loading="paying === row.orderNo"
            @click="pay(row)"
          >
            模拟支付
          </el-button>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无订单">
          <el-button type="primary" @click="router.push('/token-mall')">前往商城</el-button>
        </el-empty>
      </template>
    </el-table>
  </div>
</template>

<style scoped lang="scss">
.orders-page { display: grid; gap: 20px; width: 100%; max-width: 1240px; margin: 0 auto; }
.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; padding: 8px 0 20px; border-bottom: 1px solid var(--el-border-color); }
h1, p { margin: 0; }
h1 { font-size: 28px; line-height: 1.25; }
p, .muted { color: var(--el-text-color-secondary); font-size: 13px; }
.actions { display: flex; gap: 8px; }
@media (max-width: 680px) {
  .page-head { align-items: flex-start; flex-direction: column; }
  .orders-page :deep(.el-table__body-wrapper) { overflow-x: auto; }
}
</style>