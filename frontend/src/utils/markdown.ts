const FENCE_PATTERN = /^\s*(```|~~~)/

const isLonePipe = (line: string) => line.trim() === '|'

const isPipeRow = (line: string) => {
  const trimmed = line.trim()
  return trimmed.startsWith('|') && trimmed.endsWith('|') && trimmed.slice(1, -1).includes('|')
}

const splitPipeRow = (line: string) => line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(cell => cell.trim())

const isSeparatorCell = (cell: string) => /^:?-{3,}:?$/.test(cell.trim())

const isSeparatorRow = (line: string) => isPipeRow(line) && splitPipeRow(line).every(isSeparatorCell)

const formatPipeRow = (cells: string[]) => `| ${cells.join(' | ')} |`

const looksLikeTableAt = (lines: string[], startIndex: number) => (
  startIndex + 1 < lines.length
  && isPipeRow(lines[startIndex])
  && isSeparatorRow(lines[startIndex + 1])
)

const normalizeSeparatorCells = (cells: string[], targetLength: number) => {
  const normalized = cells.slice(0, targetLength)
  while (normalized.length < targetLength) {
    normalized.push('---')
  }
  return normalized
}

const normalizeHeaderCells = (headerCells: string[], targetLength: number, firstDataCells: string[]) => {
  const normalized = [...headerCells]
  const missingCount = targetLength - normalized.length
  if (missingCount <= 0) {
    return normalized
  }

  const firstDataCell = firstDataCells[0] || ''
  if (missingCount === 1 && /^\d+\.?$/.test(firstDataCell)) {
    normalized.unshift('')
  } else {
    normalized.push(...Array.from({ length: missingCount }, () => ''))
  }
  return normalized
}

const normalizeMalformedPipeTables = (content: string) => {
  // Fix: If header and separator rows are stuck on the same line (common issue with some tool outputs or serialization)
  // Example: | H1 | H2 ||---|---| -> | H1 | H2 |\n|---|---|
  let normalized = content.replace(/^([ \t]*\|.*\|)([ \t]*\|[-:| \t]+\|[ \t]*)$/gm, '$1\n$2')

  const lines = normalized.split('\n')
  const normalizedLines: string[] = []
  let inFence = false

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index]
    if (FENCE_PATTERN.test(line)) {
      inFence = !inFence
      normalizedLines.push(line)
      continue
    }

    if (!inFence && isLonePipe(line) && looksLikeTableAt(lines, index + 1)) {
      continue
    }

    if (!inFence && looksLikeTableAt(lines, index)) {
      const headerCells = splitPipeRow(line)
      const separatorCells = splitPipeRow(lines[index + 1])
      const firstDataCells = index + 2 < lines.length && isPipeRow(lines[index + 2])
        ? splitPipeRow(lines[index + 2])
        : []
      const targetLength = Math.max(headerCells.length, separatorCells.length, firstDataCells.length)

      if (targetLength > headerCells.length || targetLength !== separatorCells.length) {
        normalizedLines.push(formatPipeRow(normalizeHeaderCells(headerCells, targetLength, firstDataCells)))
        normalizedLines.push(formatPipeRow(normalizeSeparatorCells(separatorCells, targetLength)))
        index += 1
        continue
      }
    }

    normalizedLines.push(line)
  }

  return normalizedLines.join('\n')
}

export const normalizeMarkdownContent = (content: string) => normalizeMalformedPipeTables(content)
