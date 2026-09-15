#!/usr/bin/env node
import fs from 'node:fs'

function arg(name, fallback = '') {
  const index = process.argv.indexOf(name)
  return index >= 0 ? process.argv[index + 1] : fallback
}

function hasFlag(name) {
  return process.argv.includes(name)
}

function readJson(path, fallback) {
  if (!path || !fs.existsSync(path)) return fallback
  try {
    return JSON.parse(fs.readFileSync(path, 'utf8'))
  } catch (error) {
    throw new Error(`Invalid JSON file ${path}: ${error.message}`)
  }
}

const output = arg('--output')
if (!output) {
  console.error('Usage: node scripts/eval/build-eval-summary.mjs --output <summary.json> [--steps <steps.json>] [--jmeter <jmeter.json>] [--agent <agent.json>]')
  process.exit(2)
}

const startedAt = arg('--generated-at', new Date().toISOString())
const baseUrl = arg('--base-url', 'http://127.0.0.1:9527')
const model = arg('--model', 'deepseek-v4-flash')
const steps = readJson(arg('--steps'), [])
const jmeter = hasFlag('--skip-jmeter')
  ? { status: 'skipped', reports: [], aggregate: {} }
  : readJson(arg('--jmeter'), { status: 'skipped', reports: [], aggregate: {} })
const copilot = readJson(arg('--agent'), { status: 'skipped' })

const summary = { generatedAt: startedAt, baseUrl, model, steps, jmeter, copilot }
fs.writeFileSync(output, `${JSON.stringify(summary, null, 2)}\n`, 'utf8')
console.log(`eval summary written: ${output}`)
