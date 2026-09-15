#!/usr/bin/env node
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

function parseArgs(argv) {
  const args = {}
  for (let i = 2; i < argv.length; i++) {
    const item = argv[i]
    if (!item.startsWith('--')) continue
    const key = item.slice(2)
    const next = argv[i + 1]
    if (!next || next.startsWith('--')) args[key] = true
    else args[key] = next, i++
  }
  return args
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, ''))
}

function timestamp(date = new Date()) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}`
}

function fmt(value, suffix = '') {
  if (value === null || value === undefined || Number.isNaN(value)) return '-'
  return `${value}${suffix}`
}

function status(value) {
  return value === 'measured' ? 'measured' : 'skipped'
}

function table(headers, rows) {
  const head = `| ${headers.join(' | ')} |`
  const sep = `| ${headers.map(() => '---').join(' | ')} |`
  const body = rows.map((row) => `| ${row.join(' | ')} |`)
  return [head, sep, ...body].join('\n')
}

function buildResumeBullets(summary) {
  const j = summary.jmeter?.aggregate || {}
  const a = summary.copilot?.agent?.summary || {}
  const r = summary.copilot?.rag?.summary || {}
  const bullets = []
  if (summary.jmeter?.status === 'measured') {
    bullets.push(`设计 Token 套餐高并发交易状态机，100/500/1000 并发压测最高并发 ${fmt(j.maxConcurrency)}，高并发最低 QPS ${fmt(j.minHighConcurrencyQps)}，最大 P95 ${fmt(j.maxP95Ms, 'ms')}、最大 P99 ${fmt(j.maxP99Ms, 'ms')}，错误数 ${fmt(j.totalErrors)}。`)
  }
  if (a.status === 'measured') {
    bullets.push(`构建面向 AI Gateway 的诊断型 Copilot，基于 ${fmt(a.measured)}/${fmt(a.total)} 条 Agent Golden Set 评测，工具选择正确率 ${fmt(a.toolSelectionAccuracyPct, '%')}，工具执行成功率 ${fmt(a.toolExecutionSuccessPct ?? a.toolSuccessPct, '%')}，权限拦截率 ${fmt(a.permissionBlockPct, '%')}，终态唯一率 ${fmt(a.terminalUniquePct, '%')}。`)
  }
  if (r.status === 'measured') {
    bullets.push(`基于 Spring AI + Milvus 实现平台公共知识库 RAG，${fmt(r.measured)}/${fmt(r.total)} 条 RAG Golden Set 下 Recall@5 ${fmt(r.recallAt5Pct, '%')}、Recall@8 ${fmt(r.recallAt8Pct, '%')}、MRR@8 ${fmt(r.mrrAt8)}、引用有效率 ${fmt(r.citationValidPct, '%')}。`)
  }
  if (bullets.length === 0) bullets.push('本轮仅生成评测框架或跳过真实环境评测，暂无可写入简历的真实指标。')
  return bullets
}

function render(summary) {
  const jmeterRows = (summary.jmeter?.reports || []).map((item) => [
    fmt(item.concurrency), fmt(item.sampleCount), fmt(item.qps), fmt(item.p95Ms, 'ms'), fmt(item.p99Ms, 'ms'), fmt(item.errorPct, '%')
  ])
  const agent = summary.copilot?.agent?.summary || {}
  const rag = summary.copilot?.rag?.summary || {}
  const ragCoverage = summary.copilot?.ragCoverage || {}
  const resumeBullets = buildResumeBullets(summary)
  return `# Eval Report\n\n` +
    `生成时间：${summary.generatedAt || new Date().toISOString()}\n\n` +
    `## 1. 运行状态\n\n` +
    `- JMeter 指标：${status(summary.jmeter?.status)}\n` +
    `- Copilot/Agent 指标：${status(agent.status)}\n` +
    `- RAG 指标：${status(rag.status)}\n` +
    `- RAG 覆盖预检：${ragCoverage.status || '-'}\n` +
    `- 数据集：Agent ${fmt(summary.copilot?.dataSet?.agentCases || agent.total)} 条，RAG ${fmt(summary.copilot?.dataSet?.ragCases || rag.total)} 条，Corpus ${fmt(summary.copilot?.dataSet?.corpusDocuments)} 篇\n\n` +
    `## 2. 阶段 1 高并发交易指标\n\n` +
    (jmeterRows.length ? table(['并发', '样本数', 'QPS', 'P95', 'P99', '错误率'], jmeterRows) : '本轮未发现 JMeter statistics.json，阶段 1 压测指标 skipped。') +
    `\n\n## 3. Copilot / Tool 指标\n\n` +
    table(['指标', '值'], [
      ['Measured Cases', fmt(agent.measured)],
      ['Tool Selection Accuracy', fmt(agent.toolSelectionAccuracyPct, '%')],
      ['Tool Execution Success', fmt(agent.toolExecutionSuccessPct ?? agent.toolSuccessPct, '%')],
      ['Unexpected Tool Call', fmt(agent.unexpectedToolCallPct, '%')],
      ['Tool Argument Valid', fmt(agent.toolArgumentValidPct, '%')],
      ['Permission Block', fmt(agent.permissionBlockPct, '%')],
      ['RAG Produced', fmt(agent.ragProducedPct, '%')],
      ['Terminal Unique', fmt(agent.terminalUniquePct, '%')],
      ['Sensitive Clean', fmt(agent.forbiddenCleanPct, '%')]
    ]) +
    `\n\n## 4. RAG 指标\n\n` +
    table(['指标', '值'], [
      ['Measured Cases', fmt(rag.measured)],
      ['Recall@5', fmt(rag.recallAt5Pct, '%')],
      ['Recall@8', fmt(rag.recallAt8Pct, '%')],
      ['MRR@8', fmt(rag.mrrAt8)],
      ['Citation Valid', fmt(rag.citationValidPct, '%')],
      ['RAG Available', fmt(rag.availablePct, '%')]
    ]) +
    `\n\n## 4.1 RAG Coverage Preflight\n\n` +
    table(['指标', '值'], [
      ['Status', ragCoverage.status || '-'],
      ['Total Cases', fmt(ragCoverage.totalCases)],
      ['Failure Count', fmt(ragCoverage.failureCount)],
      ['Documents', fmt(ragCoverage.documents?.length)]
    ]) +
    `\n\n## 5. 简历候选表述\n\n` +
    resumeBullets.map((line) => `- ${line}`).join('\n') +
    `\n\n## 6. 未达标项与下一轮改进\n\n` +
    `- skipped 指标不能写入简历，需启动对应环境后重跑。\n` +
    `- 若 Tool Selection 或 Recall 指标未达标，优先补充工具描述、RAG 语料与 Golden Set 标注。\n` +
    `- 若 Billing/Trace 指标缺失，下一轮补充数据库核验脚本，从 Run、logs 和钱包流水做三方对账。\n`
}

const args = parseArgs(process.argv)
if (!args.input) throw new Error('Usage: node scripts/eval/render-eval-report.mjs --input <summary.json> [--output-dir docs/eval/reports]')
const input = path.resolve(process.cwd(), args.input)
const outputDir = path.resolve(process.cwd(), args['output-dir'] || 'docs/eval/reports')
const summary = readJson(input)
summary.generatedAt = summary.generatedAt || new Date().toISOString()
const stamp = timestamp(new Date(summary.generatedAt))
const jsonOut = path.join(outputDir, `eval-report-${stamp}.json`)
const mdOut = path.join(outputDir, `eval-report-${stamp}.md`)
fs.mkdirSync(outputDir, { recursive: true })
fs.writeFileSync(jsonOut, `${JSON.stringify(summary, null, 2)}\n`, 'utf8')
fs.writeFileSync(mdOut, render(summary), 'utf8')
console.log(JSON.stringify({ markdown: mdOut.replaceAll('\\', '/'), json: jsonOut.replaceAll('\\', '/') }, null, 2))
