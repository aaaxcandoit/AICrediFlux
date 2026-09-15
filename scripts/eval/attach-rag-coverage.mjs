#!/usr/bin/env node
import fs from 'node:fs'

function arg(name, fallback = '') {
  const index = process.argv.indexOf(name)
  return index >= 0 ? process.argv[index + 1] : fallback
}

const input = arg('--input')
const coverage = arg('--coverage')
const output = arg('--output', input)

if (!input || !coverage) {
  console.error('Usage: node scripts/eval/attach-rag-coverage.mjs --input <agent-summary.json> --coverage <coverage.json> [--output <out.json>]')
  process.exit(2)
}

function readJson(path) {
  try {
    return JSON.parse(fs.readFileSync(path, 'utf8'))
  } catch (error) {
    throw new Error(`Invalid JSON file ${path}: ${error.message}`)
  }
}

const summary = readJson(input)
summary.ragCoverage = readJson(coverage)

fs.writeFileSync(output, `${JSON.stringify(summary, null, 2)}\n`, 'utf8')
console.log(`attached ragCoverage to ${output}`)
