#!/usr/bin/env node
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const TERMINAL_EVENTS = new Set(['done', 'cancelled'])
const SECRET_VALUE_PATTERN = /(sk-[A-Za-z0-9_-]{16,}|authorization\s*[:=]\s*bearer\s+[A-Za-z0-9._~+/=-]{16,}|bearer\s+[A-Za-z0-9._~+/=-]{24,}|(?:api[_ -]?key|channel[_ -]?key|provider[_ -]?key|password|secret)\s*[:=]\s*["']?[A-Za-z0-9._~+/=-]{12,})/i
let quietProgress = false
function progress(message) {
  if (!quietProgress) console.error('[agent-eval] ' + message)
}

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
  if (!fs.existsSync(file)) return []
  return fs.readFileSync(file, 'utf8')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line, index) => {
      try { return JSON.parse(line) }
      catch (error) { throw new Error(`${file}:${index + 1} ${error.message}`) }
    })
}

function readCorpusFiles(corpusDir) {
  if (!fs.existsSync(corpusDir)) return []
  return fs.readdirSync(corpusDir)
    .filter((name) => name.endsWith('.md') || name.endsWith('.txt'))
    .sort()
    .map((name) => {
      const fullPath = path.join(corpusDir, name)
      const content = fs.readFileSync(fullPath, 'utf8')
      const base = name.replace(/\.(md|txt)$/i, '')
      const parts = base.split('__')
      const space = parts.length > 1 ? parts[0] : 'platform_docs'
      const docId = parts.length > 1 ? parts.slice(1).join('__') : base
      const h1 = content.match(/^#\s+(.+)$/m)
      return { name, fullPath, space, docId, title: h1 ? h1[1].trim() : docId, content }
    })
}

function unwrap(json) {
  if (json && typeof json === 'object') {
    if ('data' in json) return json.data
    if ('result' in json) return json.result
  }
  return json
}

async function httpJson(baseUrl, token, method, route, body) {
  progress(method + ' ' + route)
  const res = await fetch(new URL(route, baseUrl), {
    method,
    headers: {
      ...(token ? { aicrediflux: token } : {}),
      ...(body ? { 'content-type': 'application/json' } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  })
  const text = await res.text()
  let json = null
  if (text) {
    try { json = JSON.parse(text) }
    catch { json = { raw: text } }
  }
  if (!res.ok) {
    let message = json?.msg || json?.message || json?.raw || `${res.status} ${res.statusText}`
    if (res.status === 401) {
      message = `${message}. Agent/RAG eval uses Web login Sa-Token in header aicrediflux, not an API Key starting with sk-. Please log in as the target user/admin and use that login token.`
    }
    const error = new Error(message)
    error.status = res.status
    error.payload = json
    throw error
  }
  return unwrap(json)
}

async function streamEvents(baseUrl, token, runNo, timeoutMs) {
  progress('SSE /api/agent/runs/' + runNo + '/events')
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  const events = []
  let answer = ''
  try {
    const res = await fetch(new URL(`/api/agent/runs/${encodeURIComponent(runNo)}/events`, baseUrl), {
      headers: token ? { aicrediflux: token } : {},
      signal: controller.signal
    })
    if (!res.ok || !res.body) throw new Error(`SSE failed: ${res.status} ${res.statusText}`)
    const decoder = new TextDecoder()
    let buffer = ''
    for await (const chunk of res.body) {
      buffer += decoder.decode(chunk, { stream: true })
      let index
      while ((index = buffer.indexOf('\n\n')) >= 0) {
        const raw = buffer.slice(0, index)
        buffer = buffer.slice(index + 2)
        const event = parseSseBlock(raw)
        if (!event) continue
        events.push(event)
        if (event.type === 'text_delta') answer += event.data?.delta || ''
        if (TERMINAL_EVENTS.has(event.type)) return { events, answer }
      }
    }
    if (buffer.trim()) {
      const event = parseSseBlock(buffer)
      if (event) events.push(event)
    }
    return { events, answer }
  } finally {
    clearTimeout(timer)
  }
}

function parseSseBlock(raw) {
  const lines = raw.split(/\r?\n/)
  let type = 'message'
  const dataLines = []
  for (const line of lines) {
    if (line.startsWith('event:')) type = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }
  const dataText = dataLines.join('\n')
  if (!dataText && type === 'message') return null
  let data = dataText
  try { data = dataText ? JSON.parse(dataText) : {} }
  catch { data = { raw: dataText } }
  return { type, data }
}

function containsAll(text, keywords = []) {
  const normalized = String(text || '').toLowerCase()
  return keywords.every((keyword) => normalized.includes(String(keyword).toLowerCase()))
}

function intersectsText(items, keywords = []) {
  const text = JSON.stringify(items || '').toLowerCase()
  return keywords.some((keyword) => text.includes(String(keyword).toLowerCase()))
}

function isSensitiveClean(text) {
  return !SECRET_VALUE_PATTERN.test(String(text || ''))
}

function hasForbiddenLeak(items, keywords = []) {
  const text = JSON.stringify(items || '')
  return keywords.some((keyword) => {
    const value = String(keyword || '').trim()
    if (!value) return false
    const normalized = value.toLowerCase()
    if (['api key', 'api_key', 'api-key', 'authorization', 'channel key', 'provider key', 'secret', 'password', 'sk-'].includes(normalized)) {
      return !isSensitiveClean(text)
    }
    return text.toLowerCase().includes(normalized)
  })
}

function normalizeDocToken(value) {
  return String(value || '')
    .replaceAll('\\', '/')
    .split('/')
    .pop()
    .replace(/\.(md|txt)$/i, '')
    .replace(/^(platform_docs|model_docs|troubleshooting)__/, '')
    .toLowerCase()
}

function buildSeedDocIndex(seed = {}) {
  const byDocId = new Map()
  for (const doc of seed.documents || []) {
    if (doc?.docId) byDocId.set(normalizeDocToken(doc.docId), doc)
  }
  return { byDocId }
}

function matchDoc(citation, expectedDocId, seedIndex = buildSeedDocIndex()) {
  const expected = normalizeDocToken(expectedDocId)
  const directCandidates = [citation.docId, citation.sourceName, citation.docNo, citation.title, citation.locator, citation.snippet]
    .filter(Boolean)
    .map(normalizeDocToken)
  if (directCandidates.some((candidate) => candidate.includes(expected))) return true

  const seeded = seedIndex.byDocId?.get(expected)
  if (!seeded) return false
  const seededCandidates = [seeded.docNo, seeded.title].filter(Boolean).map((value) => String(value).toLowerCase())
  const citationText = [citation.docNo, citation.title, citation.locator, citation.snippet, citation.sourceName, citation.docId]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
  return seededCandidates.some((candidate) => candidate && citationText.includes(candidate))
}

function metricRatio(numerator, denominator) {
  return denominator > 0 ? Math.round((numerator / denominator) * 10000) / 100 : null
}

async function seedRagCorpus(baseUrl, adminToken, corpusDir) {
  if (!adminToken) return { status: 'skipped', reason: 'admin token missing', documents: [] }
  const corpus = readCorpusFiles(corpusDir)
  progress('Seed RAG corpus: ' + corpus.length + ' document(s)')
  const documents = []
  let index = 0
  for (const doc of corpus) {
    index++
    progress('Seed RAG ' + index + '/' + corpus.length + ': ' + doc.name)
    const created = await httpJson(baseUrl, adminToken, 'POST', '/api/admin/rag/documents', {
      space: doc.space,
      title: doc.title,
      sourceName: doc.name,
      content: doc.content
    })
    documents.push({ docId: doc.docId, title: doc.title, docNo: created?.docNo, status: created?.status })
  }
  return { status: 'measured', documents }
}

async function evaluateAgentCase(baseUrl, tokens, model, item, timeoutMs) {
  progress('Agent case ' + item.id + ': ' + item.category)
  const token = item.role === 'admin' ? tokens.admin : tokens.user
  if (!token) return { id: item.id, category: item.category, status: 'skipped', reason: `${item.role} token missing` }
  const started = Date.now()
  try {
    const session = await httpJson(baseUrl, token, 'POST', '/api/agent/sessions', {
      title: `eval-${item.id}`,
      model
    })
    const submission = await httpJson(baseUrl, token, 'POST', `/api/agent/sessions/${encodeURIComponent(session.sessionNo)}/messages`, {
      content: item.question,
      model
    })
    const stream = await streamEvents(baseUrl, token, submission.runNo, timeoutMs)
    const events = stream.events
    const toolEvents = events.filter((event) => event.type === 'tool_start' || event.type === 'tool_result')
    const toolResultEvents = events.filter((event) => event.type === 'tool_result')
    const usedTools = [...new Set(toolEvents.map((event) => event.data?.toolName).filter(Boolean))]
    const ragEvents = events.filter((event) => event.type === 'rag_refs')
    const citations = ragEvents.flatMap((event) => event.data?.citations || [])
    const errorEvents = events.filter((event) => event.type === 'error')
    const terminalCount = events.filter((event) => TERMINAL_EVENTS.has(event.type)).length
    const expectedTools = item.expectedTools || []
    const toolSelected = expectedTools.length === 0
      ? usedTools.length === 0
      : expectedTools.every((tool) => usedTools.includes(tool))
    const deniedToolResults = toolResultEvents.filter((event) => /DENIED|PERMISSION_DENIED/i.test(String(event.data?.status || '') + ' ' + JSON.stringify(event.data || {})))
    const invalidArgumentToolResults = toolResultEvents.filter((event) => /INVALID_ARGUMENT/i.test(String(event.data?.status || '') + ' ' + JSON.stringify(event.data || {})))
    const answerSaysPermission = /权限|管理员|只能自己/.test(stream.answer)
    const permissionDenied = item.expectPermissionDenied ? deniedToolResults.length > 0 && answerSaysPermission : errorEvents.some((event) => /权限|admin|管理员|forbidden|unauthorized/i.test(event.data?.message || '')) || answerSaysPermission
    const expectedKeywordsOk = containsAll(stream.answer, item.expectedKeywords || [])
    const forbiddenClean = isSensitiveClean(JSON.stringify({ answer: stream.answer, toolEvents, citations })) && !hasForbiddenLeak({ answer: stream.answer, toolEvents, citations }, item.forbiddenKeywords || [])
    const toolFailures = toolResultEvents.filter((event) => /FAIL|ERROR/i.test(event.data?.status || '') && !/DENIED/i.test(event.data?.status || '')).length
    const unexpectedTools = usedTools.filter((tool) => !expectedTools.includes(tool))
    const unexpectedToolCall = unexpectedTools.length > 0 || (expectedTools.length === 0 && usedTools.length > 0)
    const toolArgumentValid = invalidArgumentToolResults.length === 0
    const toolExecutionSuccess = expectedTools.length === 0 ? true : toolFailures === 0 && deniedToolResults.length === 0 && usedTools.length > 0
    return {
      id: item.id,
      category: item.category,
      role: item.role,
      status: 'measured',
      latencyMs: Date.now() - started,
      runNo: submission.runNo,
      usedTools,
      expectedTools,
      toolSelected,
      toolSuccess: toolExecutionSuccess,
      toolExecutionSuccess,
      unexpectedTools,
      unexpectedToolCall,
      toolArgumentValid,
      ragExpected: Boolean(item.expectedRag),
      ragProduced: citations.length > 0,
      permissionExpected: Boolean(item.expectPermissionDenied),
      permissionDenied,
      expectedKeywordsOk,
      forbiddenClean,
      terminalUnique: terminalCount <= 1,
      terminalCount,
      usage: events.findLast?.((event) => event.type === 'usage')?.data || events.filter((event) => event.type === 'usage').slice(-1)[0]?.data || null,
      answerPreview: stream.answer.slice(0, 300),
      error: errorEvents[0]?.data?.message || ''
    }
  } catch (error) {
    return {
      id: item.id,
      category: item.category,
      role: item.role,
      status: 'measured',
      latencyMs: Date.now() - started,
      usedTools: [],
      expectedTools: item.expectedTools || [],
      toolSelected: false,
      toolSuccess: false,
      toolExecutionSuccess: false,
      unexpectedTools: [],
      unexpectedToolCall: false,
      toolArgumentValid: false,
      ragExpected: Boolean(item.expectedRag),
      ragProduced: false,
      permissionExpected: Boolean(item.expectPermissionDenied),
      permissionDenied: /权限|admin|管理员|forbidden|unauthorized/i.test(error.message || ''),
      expectedKeywordsOk: false,
      forbiddenClean: isSensitiveClean(error.message || ''),
      terminalUnique: false,
      terminalCount: 0,
      usage: null,
      answerPreview: '',
      error: error.message || String(error)
    }
  }
}

async function evaluateRagCase(baseUrl, userToken, item, seedIndex = buildSeedDocIndex()) {
  progress('RAG case ' + item.id)
  if (!userToken) return { id: item.id, status: 'skipped', reason: 'user token missing' }
  try {
    const result = await httpJson(baseUrl, userToken, 'GET', `/api/agent/rag/search?q=${encodeURIComponent(item.question)}`)
    const citations = result?.citations || []
    const expected = item.expectedDocIds || []
    const hitRanks = expected.map((docId) => citations.findIndex((citation) => matchDoc(citation, docId, seedIndex))).filter((index) => index >= 0)
    const firstRank = hitRanks.length ? Math.min(...hitRanks) + 1 : null
    const recallAt5 = expected.length ? expected.filter((docId) => citations.slice(0, 5).some((citation) => matchDoc(citation, docId, seedIndex))).length / expected.length : 1
    const recallAt8 = expected.length ? expected.filter((docId) => citations.slice(0, 8).some((citation) => matchDoc(citation, docId, seedIndex))).length / expected.length : 1
    const citationText = citations.map((citation) => `${citation.docId || ''} ${citation.sourceName || ''} ${citation.docNo || ''} ${citation.title || ''} ${citation.locator || ''} ${citation.snippet || ''}`).join('\n')
    return {
      id: item.id,
      status: 'measured',
      available: Boolean(result?.available),
      citationCount: citations.length,
      expectedDocIds: expected,
      hitRanks: hitRanks.map((index) => index + 1),
      firstRank,
      reciprocalRank: firstRank ? 1 / firstRank : 0,
      recallAt5,
      recallAt8,
      citationValid: citations.length > 0 && containsAll(citationText, item.expectedKeywords || []),
      message: result?.message || ''
    }
  } catch (error) {
    return { id: item.id, status: 'measured', available: false, citationCount: 0, expectedDocIds: item.expectedDocIds || [], hitRanks: [], firstRank: null, reciprocalRank: 0, recallAt5: 0, recallAt8: 0, citationValid: false, message: error.message || String(error) }
  }
}

function summarizeAgent(results) {
  const measured = results.filter((item) => item.status === 'measured')
  const toolCases = measured.filter((item) => item.expectedTools?.length > 0)
  const executableToolCases = toolCases.filter((item) => !item.permissionExpected && item.category !== 'cancel')
  const permissionCases = measured.filter((item) => item.permissionExpected)
  const ragCases = measured.filter((item) => item.ragExpected)
  return {
    status: measured.length ? 'measured' : 'skipped',
    total: results.length,
    measured: measured.length,
    skipped: results.length - measured.length,
    toolSelectionAccuracyPct: metricRatio(toolCases.filter((item) => item.toolSelected).length, toolCases.length),
    toolSuccessPct: metricRatio(executableToolCases.filter((item) => item.toolExecutionSuccess).length, executableToolCases.length),
    toolExecutionSuccessPct: metricRatio(executableToolCases.filter((item) => item.toolExecutionSuccess).length, executableToolCases.length),
    unexpectedToolCallPct: metricRatio(measured.filter((item) => item.unexpectedToolCall).length, measured.length),
    toolArgumentValidPct: metricRatio(toolCases.filter((item) => item.toolArgumentValid).length, toolCases.length),
    permissionBlockPct: metricRatio(permissionCases.filter((item) => item.permissionDenied).length, permissionCases.length),
    ragProducedPct: metricRatio(ragCases.filter((item) => item.ragProduced).length, ragCases.length),
    terminalUniquePct: metricRatio(measured.filter((item) => item.terminalUnique).length, measured.length),
    forbiddenCleanPct: metricRatio(measured.filter((item) => item.forbiddenClean).length, measured.length),
    keywordPassPct: metricRatio(measured.filter((item) => item.expectedKeywordsOk || item.permissionDenied).length, measured.length)
  }
}

function summarizeRag(results) {
  const measured = results.filter((item) => item.status === 'measured')
  const recall5 = measured.reduce((sum, item) => sum + (item.recallAt5 || 0), 0) / (measured.length || 1)
  const recall8 = measured.reduce((sum, item) => sum + (item.recallAt8 || 0), 0) / (measured.length || 1)
  const mrr8 = measured.reduce((sum, item) => sum + (item.reciprocalRank || 0), 0) / (measured.length || 1)
  return {
    status: measured.length ? 'measured' : 'skipped',
    total: results.length,
    measured: measured.length,
    skipped: results.length - measured.length,
    recallAt5Pct: measured.length ? Math.round(recall5 * 10000) / 100 : null,
    recallAt8Pct: measured.length ? Math.round(recall8 * 10000) / 100 : null,
    mrrAt8: measured.length ? Math.round(mrr8 * 1000) / 1000 : null,
    citationValidPct: metricRatio(measured.filter((item) => item.citationValid).length, measured.length),
    availablePct: metricRatio(measured.filter((item) => item.available).length, measured.length)
  }
}

function assertSelfTest(condition, message) {
  if (!condition) throw new Error(`self-test failed: ${message}`)
}

function runSelfTest() {
  const seedIndex = buildSeedDocIndex({ documents: [
    { docId: 'siliconflow-onboarding', docNo: 'RD123', title: 'SiliconFlow 接入指南' }
  ] })
  assertSelfTest(isSensitiveClean('可以在 Authorization header 里传 API Key，但不要泄露真实值。'), 'plain API Key concept should be allowed')
  assertSelfTest(isSensitiveClean('API Key 通常以 sk- 前缀展示为占位示例。'), 'plain sk- prefix concept should be allowed')
  assertSelfTest(!isSensitiveClean('Authorization: Bearer sk-real-secret-1234567890'), 'real bearer secret should be blocked')
  assertSelfTest(matchDoc({ docId: 'siliconflow-onboarding', sourceName: 'platform_docs__siliconflow-onboarding.md' }, 'siliconflow-onboarding', seedIndex), 'docId/sourceName should match expected doc id')
  assertSelfTest(matchDoc({ docNo: 'RD123', title: 'SiliconFlow 接入指南' }, 'siliconflow-onboarding', seedIndex), 'seed docNo/title fallback should match expected doc id')
  const summary = summarizeAgent([{ status: 'measured', category: 'permission', expectedTools: ['channelStatus'], permissionExpected: true, toolSelected: true, toolExecutionSuccess: false, permissionDenied: true, ragExpected: false, terminalUnique: true, forbiddenClean: true, expectedKeywordsOk: false, unexpectedToolCall: false, toolArgumentValid: true }])
  assertSelfTest(summary.permissionBlockPct === 100, 'permission cases should count as permission block success')
  assertSelfTest(summary.toolExecutionSuccessPct === null, 'permission cases should not lower tool execution success')
}
const args = parseArgs(process.argv)
quietProgress = Boolean(args.quiet)
if (args['self-test']) {
  runSelfTest()
  console.log('copilot-golden-eval self-test passed')
  process.exit(0)
}
const root = process.cwd()
const baseUrl = args['base-url'] || process.env.AICREDIFLUX_EVAL_BASE_URL || 'http://127.0.0.1:9527'
const userToken = args['user-token'] || process.env.AICREDIFLUX_EVAL_USER_TOKEN || ''
const adminToken = args['admin-token'] || process.env.AICREDIFLUX_EVAL_ADMIN_TOKEN || ''
const model = args.model || process.env.AICREDIFLUX_EVAL_MODEL || 'deepseek-v4-flash'
const timeoutMs = Number(args.timeout || process.env.AICREDIFLUX_EVAL_TIMEOUT_MS || 120000)
const agentSetPath = path.resolve(root, args['agent-set'] || 'data/eval/golden/agent-golden-set.jsonl')
const ragSetPath = path.resolve(root, args['rag-set'] || 'data/eval/golden/rag-golden-set.jsonl')
const corpusDir = path.resolve(root, args.corpus || 'data/eval/corpus')
const output = args.output ? path.resolve(root, args.output) : null
const skipLive = Boolean(args['skip-live'])

const agentSet = readJsonl(agentSetPath)
const ragSet = readJsonl(ragSetPath)
const result = {
  status: skipLive ? 'skipped' : 'measured',
  generatedAt: new Date().toISOString(),
  baseUrl,
  model,
  dataSet: {
    agentSet: agentSetPath.replaceAll('\\', '/'),
    agentCases: agentSet.length,
    ragSet: ragSetPath.replaceAll('\\', '/'),
    ragCases: ragSet.length,
    corpus: corpusDir.replaceAll('\\', '/'),
    corpusDocuments: readCorpusFiles(corpusDir).length
  },
  seed: { status: 'skipped' },
  agent: { summary: { status: 'skipped', total: agentSet.length, measured: 0, skipped: agentSet.length }, cases: [] },
  rag: { summary: { status: 'skipped', total: ragSet.length, measured: 0, skipped: ragSet.length }, cases: [] }
}

if (!skipLive) {
  progress('Start live eval: agentCases=' + agentSet.length + ', ragCases=' + ragSet.length + ', model=' + model)
  if (args['seed-rag']) result.seed = await seedRagCorpus(baseUrl, adminToken, corpusDir)
  const agentCases = []
  let agentIndex = 0
  for (const item of agentSet) {
    agentIndex++
    progress('Run Agent ' + agentIndex + '/' + agentSet.length)
    agentCases.push(await evaluateAgentCase(baseUrl, { user: userToken, admin: adminToken }, model, item, timeoutMs))
  }
  const ragCases = []
  const seedIndex = buildSeedDocIndex(result.seed)
  let ragIndex = 0
  for (const item of ragSet) {
    ragIndex++
    progress('Run RAG ' + ragIndex + '/' + ragSet.length)
    ragCases.push(await evaluateRagCase(baseUrl, userToken, item, seedIndex))
  }
  result.agent = { summary: summarizeAgent(agentCases), cases: agentCases }
  result.rag = { summary: summarizeRag(ragCases), cases: ragCases }
}

if (skipLive) progress('Skip live eval: agentCases=' + agentSet.length + ', ragCases=' + ragSet.length)
if (output) {
  fs.mkdirSync(path.dirname(output), { recursive: true })
  fs.writeFileSync(output, `${JSON.stringify(result, null, 2)}\n`, 'utf8')
}
console.log(JSON.stringify(result, null, 2))





