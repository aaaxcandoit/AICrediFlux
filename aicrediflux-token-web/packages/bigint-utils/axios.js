const MAX_SAFE_INTEGER_BIGINT = BigInt(Number.MAX_SAFE_INTEGER)

function isJsonContentType(headers) {
  const contentType =
    typeof headers?.get === 'function'
      ? headers.get('content-type')
      : headers?.['content-type'] || headers?.['Content-Type']
  return typeof contentType === 'string' && /(^|[/+])json($|;)/i.test(contentType)
}

function looksLikeJson(data) {
  const trimmed = data.trim()
  return /^[{["tfn0-9-]/.test(trimmed)
}

function readStringLiteral(input, start) {
  let index = start + 1
  while (index < input.length) {
    const char = input[index]
    if (char === '\\') {
      index += 2
      continue
    }
    if (char === '"') {
      return index + 1
    }
    index += 1
  }
  return input.length
}

function readNumberLiteral(input, start) {
  let index = start
  let isInteger = true

  if (input[index] === '-') {
    index += 1
  }

  if (input[index] === '0') {
    index += 1
  } else {
    while (index < input.length && input[index] >= '0' && input[index] <= '9') {
      index += 1
    }
  }

  if (input[index] === '.') {
    isInteger = false
    index += 1
    while (index < input.length && input[index] >= '0' && input[index] <= '9') {
      index += 1
    }
  }

  if (input[index] === 'e' || input[index] === 'E') {
    isInteger = false
    index += 1
    if (input[index] === '+' || input[index] === '-') {
      index += 1
    }
    while (index < input.length && input[index] >= '0' && input[index] <= '9') {
      index += 1
    }
  }

  return { end: index, isInteger }
}

function isUnsafeIntegerLiteral(literal) {
  const unsigned = literal.startsWith('-') ? literal.slice(1) : literal
  if (unsigned.length < 16) return false

  try {
    return BigInt(unsigned) > MAX_SAFE_INTEGER_BIGINT
  } catch {
    return true
  }
}

function quoteUnsafeIntegerLiterals(json) {
  let output = ''
  let index = 0

  while (index < json.length) {
    const char = json[index]

    if (char === '"') {
      const end = readStringLiteral(json, index)
      output += json.slice(index, end)
      index = end
      continue
    }

    if (char === '-' || (char >= '0' && char <= '9')) {
      const { end, isInteger } = readNumberLiteral(json, index)
      const literal = json.slice(index, end)
      output += isInteger && isUnsafeIntegerLiteral(literal) ? `"${literal}"` : literal
      index = end
      continue
    }

    output += char
    index += 1
  }

  return output
}

export function parseJsonWithBigint(data) {
  return JSON.parse(quoteUnsafeIntegerLiterals(data))
}

export function setupBigintAxios(instance) {
  instance.defaults.transformResponse = [
    function bigintTransformResponse(data, headers) {
      if (typeof data !== 'string' || data.length === 0) {
        return data
      }

      if (!isJsonContentType(headers) && !looksLikeJson(data)) {
        return data
      }

      try {
        return parseJsonWithBigint(data)
      } catch {
        return data
      }
    }
  ]

  return instance
}