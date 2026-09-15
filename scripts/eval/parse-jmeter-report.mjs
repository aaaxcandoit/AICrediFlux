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
  return JSON.parse(fs.readFileSync(file, 'utf8'))
}

function round(value, digits = 2) {
  if (typeof value !== 'number' || Number.isNaN(value)) return null
  const factor = 10 ** digits
  return Math.round(value * factor) / factor
}

function extractConcurrency(name) {
  const match = name.match(/report-(\d+)/)
  return match ? Number(match[1]) : null
}

function metricFromReport(reportDir) {
  const statisticsPath = path.join(reportDir, 'statistics.json')
  const stats = readJson(statisticsPath)
  const total = stats.Total || stats.total || Object.values(stats)[0]
  if (!total) throw new Error(`No Total metric in ${statisticsPath}`)
  const concurrency = extractConcurrency(path.basename(reportDir))
  return {
    concurrency,
    reportDir: reportDir.replaceAll('\\', '/'),
    sampleCount: Number(total.sampleCount || 0),
    errorCount: Number(total.errorCount || 0),
    errorPct: round(Number(total.errorPct || 0), 4),
    qps: round(Number(total.throughput || 0), 2),
    meanMs: round(Number(total.meanResTime || 0), 2),
    p90Ms: round(Number(total.pct1ResTime || 0), 2),
    p95Ms: round(Number(total.pct2ResTime || 0), 2),
    p99Ms: round(Number(total.pct3ResTime || 0), 2)
  }
}

const args = parseArgs(process.argv)
const root = process.cwd()
const resultsDir = path.resolve(root, args['results-dir'] || 'scripts/jmeter/results')
const out = args.output ? path.resolve(root, args.output) : null
const reports = []

if (fs.existsSync(resultsDir)) {
  for (const entry of fs.readdirSync(resultsDir, { withFileTypes: true })) {
    if (!entry.isDirectory() || !entry.name.startsWith('report-')) continue
    const statisticsPath = path.join(resultsDir, entry.name, 'statistics.json')
    if (fs.existsSync(statisticsPath)) reports.push(metricFromReport(path.join(resultsDir, entry.name)))
  }
}

reports.sort((a, b) => (a.concurrency || 0) - (b.concurrency || 0))
const highConcurrency = reports.filter((item) => (item.concurrency || 0) >= 500)
const summary = {
  status: reports.length > 0 ? 'measured' : 'skipped',
  generatedAt: new Date().toISOString(),
  sourceDir: resultsDir.replaceAll('\\', '/'),
  reports,
  aggregate: {
    reportCount: reports.length,
    maxConcurrency: reports.reduce((max, item) => Math.max(max, item.concurrency || 0), 0),
    minHighConcurrencyQps: highConcurrency.length ? round(Math.min(...highConcurrency.map((item) => item.qps || 0)), 2) : null,
    maxP95Ms: reports.length ? round(Math.max(...reports.map((item) => item.p95Ms || 0)), 2) : null,
    maxP99Ms: reports.length ? round(Math.max(...reports.map((item) => item.p99Ms || 0)), 2) : null,
    totalErrors: reports.reduce((sum, item) => sum + item.errorCount, 0),
    maxErrorPct: reports.length ? round(Math.max(...reports.map((item) => item.errorPct || 0)), 4) : null
  }
}

if (out) {
  fs.mkdirSync(path.dirname(out), { recursive: true })
  fs.writeFileSync(out, `${JSON.stringify(summary, null, 2)}\n`, 'utf8')
}

console.log(JSON.stringify(summary, null, 2))