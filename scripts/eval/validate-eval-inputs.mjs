#!/usr/bin/env node
import fs from 'node:fs'

function arg(name, fallback = '') {
  const index = process.argv.indexOf(name)
  return index >= 0 ? process.argv[index + 1] : fallback
}

function readJson(file, label) {
  if (!fs.existsSync(file)) throw new Error(`${label} not found: ${file}`)
  return JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, ''))
}

const agentPath = arg('--agent')
const jmeterPath = arg('--jmeter')
const agent = readJson(agentPath, 'Agent result')
const jmeter = readJson(jmeterPath, 'JMeter result')

const agentStatus = agent.agent?.summary?.status
const ragStatus = agent.rag?.summary?.status
if (agentStatus !== 'measured' || ragStatus !== 'measured') {
  throw new Error(`Agent/RAG result is not full measured data: agent=${agentStatus || '-'}, rag=${ragStatus || '-'}. Run: powershell -ExecutionPolicy Bypass -File .\\scripts\\agent-eval.ps1 -SeedRag`)
}

if (jmeter.status !== 'measured' || !Array.isArray(jmeter.reports) || jmeter.reports.length === 0) {
  throw new Error(`JMeter result is not measured data: status=${jmeter.status || '-'}, reports=${jmeter.reports?.length || 0}. Run scripts/jmeter-eval.ps1 first.`)
}

console.log(`eval inputs ok: agent=${agent.agent.summary.measured}/${agent.agent.summary.total}, rag=${agent.rag.summary.measured}/${agent.rag.summary.total}, jmeterReports=${jmeter.reports.length}`)
