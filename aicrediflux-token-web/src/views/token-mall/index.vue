<script setup lang="ts">
import { computed, onActivated, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createTokenOrder,
  getFlashSaleCampaigns,
  getFlashSaleWallet,
  getTokenPackages,
  submitFlashSale
} from '@/api/token-mall'
import type { FlashSaleCampaign, IntegerValue, TokenPackage } from '@/api/token-mall/types'
import { formatQuotaWithCurrency } from '@/utils/currency'

const router = useRouter()
const loading = ref(false)
const busyId = ref('')
const packages = ref<TokenPackage[]>([])
const campaigns = ref<FlashSaleCampaign[]>([])
const balance = ref<IntegerValue>(0)
const serverOffset = ref(0)
const tick = ref(Date.now())
let timer: number | undefined

const now = computed(() => tick.value + serverOffset.value)
const numberText = (value: IntegerValue) => value.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
const balanceText = computed(() => formatQuotaWithCurrency(balance.value))
const money = (value: IntegerValue) => '¥' + (Number(value) / 100).toFixed(2)
const asTime = (value: number | string) => new Date(value).getTime()
const countdown = (campaign: FlashSaleCampaign) => {
  const target = now.value < asTime(campaign.startTime) ? asTime(campaign.startTime) : asTime(campaign.endTime)
  const seconds = Math.max(0, Math.floor((target - now.value) / 1000))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remain = seconds % 60
  return [hours, minutes, remain].map((item) => String(item).padStart(2, '0')).join(':')
}
const campaignState = (campaign: FlashSaleCampaign) => {
  if (now.value < asTime(campaign.startTime)) return '即将开始'
  if (now.value >= asTime(campaign.endTime)) return '已结束'
  if (campaign.availableStock <= 0) return '已售罄'
  return '抢购中'
}
const canBuy = (campaign: FlashSaleCampaign) =>
  campaignState(campaign) === '抢购中' && campaign.availableStock > 0

async function load() {
  loading.value = true
  try {
    const [packageData, campaignData, walletData] = await Promise.all([
      getTokenPackages(),
      getFlashSaleCampaigns(),
      getFlashSaleWallet()
    ])
    packages.value = packageData
    campaigns.value = campaignData.campaigns
    balance.value = walletData.balance
    serverOffset.value = asTime(campaignData.serverTime) - Date.now()
  } finally {
    loading.value = false
  }
}

async function ordinaryBuy(raw: unknown) {
  const pack = raw as TokenPackage
  busyId.value = 'package-' + pack.id
  try {
    const order = await createTokenOrder(pack.id)
    ElMessage.success('订单已创建，请在 15 分钟内完成支付')
    await router.push({ path: '/orders/token', query: { orderNo: order.orderNo } })
  } finally {
    busyId.value = ''
  }
}

async function flashBuy(campaign: FlashSaleCampaign) {
  busyId.value = 'campaign-' + campaign.id
  try {
    const submission = await submitFlashSale(campaign.id)
    ElMessage.success('抢购请求已受理，正在创建订单')
    await router.push({ path: '/orders/token', query: { orderNo: submission.orderNo } })
  } finally {
    busyId.value = ''
  }
}

onMounted(() => {
  load()
  timer = window.setInterval(() => { tick.value = Date.now() }, 1000)
})
onActivated(load)
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <div class="token-mall" v-loading="loading">
    <header class="page-head">
      <div>
        <h1>AI Credit 商城</h1>
        <p>购买套餐后，AI Credit 会进入钱包，可用于 API / Playground / Agent 模型调用。</p>
      </div>
      <div class="balance">
        <div class="balance__copy">
          <span>AI Credit 钱包余额</span>
          <small>模型调用会从这里扣除</small>
        </div>
        <strong>{{ balanceText }}</strong>
        <el-button circle title="刷新" aria-label="刷新" @click="load">
          <i class="i-ep-refresh" />
        </el-button>
      </div>
    </header>

    <section v-if="campaigns.length" class="campaigns">
      <div class="section-head">
        <h2>限时抢购</h2>
        <span>倒计时已按服务器时间校准</span>
      </div>
      <div class="campaign-list">
        <article v-for="campaign in campaigns" :key="campaign.id.toString()" class="campaign-row">
          <div class="campaign-name">
            <el-tag :type="canBuy(campaign) ? 'danger' : 'info'" effect="plain">
              {{ campaignState(campaign) }}
            </el-tag>
            <div>
              <h3>{{ campaign.activityName }}</h3>
              <p>到账 {{ numberText(campaign.creditAmount) }} AI Credit</p>
            </div>
          </div>
          <div class="campaign-clock">
            <span>{{ now < asTime(campaign.startTime) ? '距开始' : '距结束' }}</span>
            <strong>{{ countdown(campaign) }}</strong>
          </div>
          <div class="stock">
            <span>剩余</span>
            <strong>{{ campaign.availableStock }} / {{ campaign.totalStock }}</strong>
          </div>
          <div class="price">{{ money(campaign.flashPrice) }}</div>
          <el-button
            type="danger"
            :disabled="!canBuy(campaign)"
            :loading="busyId === 'campaign-' + campaign.id"
            @click="flashBuy(campaign)"
          >
            立即抢购
          </el-button>
        </article>
      </div>
    </section>

    <section>
      <div class="section-head">
        <h2>常规套餐</h2>
        <el-button text @click="router.push('/orders/token')">查看订单</el-button>
      </div>
      <el-table :data="packages" stripe>
        <el-table-column prop="name" label="套餐" min-width="180">
          <template #default="{ row }">
            <strong>{{ row.name }}</strong>
            <div class="muted">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="到账 AI Credit" min-width="150">
          <template #default="{ row }">{{ numberText(row.creditAmount) }}</template>
        </el-table-column>
        <el-table-column label="价格" min-width="130">
          <template #default="{ row }">
            <span class="sale-price">{{ money(row.salePrice) }}</span>
            <del v-if="row.originalPrice !== row.salePrice">{{ money(row.originalPrice) }}</del>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="128" align="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              :loading="busyId === 'package-' + row.id"
              @click="ordinaryBuy(row)"
            >
              创建订单
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped lang="scss">
.token-mall { display: grid; gap: 24px; max-width: 1180px; width: 100%; margin: 0 auto; }
.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; padding: 8px 0 20px; border-bottom: 1px solid var(--el-border-color); }
h1, h2, h3, p { margin: 0; }
h1 { font-size: 28px; line-height: 1.25; }
h2 { font-size: 18px; }
h3 { font-size: 15px; }
.page-head p, .muted, .section-head span, .campaign-name p, .stock span, .campaign-clock span { color: var(--el-text-color-secondary); font-size: 13px; }
.balance { display: grid; grid-template-columns: auto auto 32px; align-items: center; gap: 10px; justify-content: end; }
.balance__copy { display: grid; gap: 2px; text-align: right; }
.balance__copy small { color: var(--el-text-color-secondary); font-size: 12px; }
.balance strong { font-size: 24px; font-variant-numeric: tabular-nums; }
.section-head { display: flex; align-items: center; justify-content: space-between; min-height: 40px; margin-bottom: 8px; }
.campaign-list { border-top: 1px solid var(--el-border-color); }
.campaign-row { display: grid; grid-template-columns: minmax(220px, 2fr) 120px 110px 100px 112px; align-items: center; gap: 16px; min-height: 82px; padding: 12px 0; border-bottom: 1px solid var(--el-border-color); }
.campaign-name { display: flex; align-items: center; gap: 12px; min-width: 0; }
.campaign-clock, .stock { display: grid; gap: 3px; }
.campaign-clock strong { font-family: 'JetBrains Mono Variable', monospace; font-size: 16px; letter-spacing: 0; }
.price, .sale-price { color: var(--el-color-danger); font-weight: 700; }
del { display: block; color: var(--el-text-color-placeholder); font-size: 12px; }
.muted { margin-top: 3px; }
@media (max-width: 760px) {
  .page-head { align-items: flex-start; flex-direction: column; }
  .campaign-row { grid-template-columns: 1fr auto; gap: 10px; }
  .campaign-clock, .stock { grid-row: 2; }
  .price { text-align: right; }
  .campaign-row .el-button { grid-column: 2; grid-row: 2 / span 2; align-self: end; }
}
</style>