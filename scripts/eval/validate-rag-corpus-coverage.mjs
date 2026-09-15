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

function readJsonl(file) {
  return fs.readFileSync(file, 'utf8')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line, index) => {
      try { return JSON.parse(line) }
      catch (error) { throw new Error(`${file}:${index + 1} ${error.message}`) }
    })
}

function normalize(value) {
  return String(value || '').toLowerCase()
}

function corpusFile(corpusDir, space, docId) {
  return path.join(corpusDir, `${space}__${docId}.md`)
}

function main() {
  const args = parseArgs(process.argv)
  const root = process.cwd()
  const ragSetPath = path.resolve(root, args['rag-set'] || 'data/eval/golden/rag-golden-set.jsonl')
  const corpusDir = path.resolve(root, args.corpus || 'data/eval/corpus')
  const cases = readJsonl(ragSetPath)
  const byDoc = new Map()
  const failures = []

  for (const item of cases) {
    const expectedDocIds = item.expectedDocIds || []
    if (!item.space) failures.push({ id: item.id, reason: 'missing space' })
    if (!expectedDocIds.length) failures.push({ id: item.id, reason: 'missing expectedDocIds' })
    for (const docId of expectedDocIds) {
      const file = corpusFile(corpusDir, item.space, docId)
      if (!fs.existsSync(file)) {
        failures.push({ id: item.id, docId, reason: `missing corpus file ${path.relative(root, file)}` })
        continue
      }
      const content = fs.readFileSync(file, 'utf8')
      const text = normalize(content)
      const missingKeywords = (item.expectedKeywords || []).filter((keyword) => !text.includes(normalize(keyword)))
      const key = `${item.space}/${docId}`
      const stat = byDoc.get(key) || { doc: key, cases: 0, passed: 0, missing: [] }
      stat.cases++
      if (missingKeywords.length) {
        failures.push({ id: item.id, docId, reason: 'missing expectedKeywords', missingKeywords })
        stat.missing.push({ id: item.id, missingKeywords })
      } else {
        stat.passed++
      }
      byDoc.set(key, stat)
    }
  }

  const documents = [...byDoc.values()].sort((a, b) => a.doc.localeCompare(b.doc)).map((item) => ({
    doc: item.doc,
    cases: item.cases,
    passed: item.passed,
    coveragePct: item.cases ? Math.round(item.passed * 10000 / item.cases) / 100 : 0,
    missing: item.missing
  }))
  const summary = {
    status: failures.length ? 'failed' : 'passed',
    ragSet: ragSetPath.replaceAll('\\', '/'),
    corpus: corpusDir.replaceAll('\\', '/'),
    totalCases: cases.length,
    documents,
    failureCount: failures.length,
    failures
  }
  console.log(JSON.stringify(summary, null, 2))
  if (failures.length) process.exit(1)
}

main()
