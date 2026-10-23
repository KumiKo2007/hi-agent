"use client"

import ReactMarkdown from "react-markdown"
import remarkMath from "remark-math"
import rehypeKatex from "rehype-katex"
import type { Root } from "hast"
import type { VFile } from "vfile"

// rehype-katex会将部分失败公式退回红色文本；保留其诊断并另给出可访问、明确的错误状态。
function rehypeFormulaErrors() {
  return (tree: Root, file: VFile) => {
    for (const message of file.messages.filter(item => item.source === "rehype-katex").slice(0, 10)) {
      tree.children.push({ type: "element", tagName: "p", properties: { role: "alert", className: ["text-destructive"] },
        children: [{ type: "text", value: `公式解析失败：${message.cause instanceof Error ? message.cause.message : message.reason}` }] })
    }
  }
}

/** 只渲染受限 Markdown 与 KaTeX，不信任模型 HTML、链接、图片或数学扩展命令。 */
export function LessonMath({ children }: { children: string }) {
  return <div className="lesson-math prose prose-sm max-w-none break-words dark:prose-invert">
    <ReactMarkdown skipHtml remarkPlugins={[remarkMath]} rehypePlugins={[[rehypeKatex, {
      trust: false, strict: "error", maxExpand: 1000, maxSize: 20, output: "htmlAndMathml",
    }], rehypeFormulaErrors]} urlTransform={() => ""} components={{
      a: ({ children }) => <span>{children}</span>,
      img: () => <span role="note">外部图片未加载，请使用保存的教学图形。</span>,
      span: ({ className, children, title, style, "aria-hidden": ariaHidden }) => className?.includes("katex-error")
        ? <span role="alert" className="text-destructive">公式解析失败：<code>{children}</code></span>
        : <span className={className} title={title} style={style} aria-hidden={ariaHidden}>{children}</span>,
    }}>{children}</ReactMarkdown>
  </div>
}
