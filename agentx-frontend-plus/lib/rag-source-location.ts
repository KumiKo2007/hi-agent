/** 只有 PDF 文件位置可称物理页；Word 使用处理时保存的原文定位。 */
export function ragSourceLocation(source: { sourceLocatorType?: string | null; sourceLocator?: string | null; page: number }, fileName: string) {
  if (source.sourceLocatorType === "WORD_PARAGRAPH" && source.sourceLocator) {
    try {
      const locator = JSON.parse(source.sourceLocator)
      if (Number.isInteger(locator.paragraphIndex) && locator.paragraphIndex > 0) {
        return `原文第 ${locator.paragraphIndex} 段${locator.heading ? ` · ${locator.heading}` : ""}`
      }
    } catch { /* 损坏或历史定位不猜测原文位置。 */ }
  }
  return /\.pdf$/i.test(fileName) ? `文件物理第 ${source.page + 1} 页` : `来源处理单元 ${source.page + 1}（需重新处理以核查原文定位）`
}

export function ragCoverageWarnings(source: { coverageWarnings?: string | null }): string[] {
  if (!source.coverageWarnings) return []
  try {
    const warnings = JSON.parse(source.coverageWarnings)
    return Array.isArray(warnings) ? warnings.filter((warning): warning is string => typeof warning === "string") : ["来源覆盖信息损坏，请重新处理"]
  } catch { return ["来源覆盖信息损坏，请重新处理"] }
}
