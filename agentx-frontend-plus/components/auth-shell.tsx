import type React from "react"
import { BrandMark } from "@/components/brand-mark"

type AuthShellProps = {
  title: string
  subtitle?: string
  children: React.ReactNode
}

/**
 * 认证页面统一外壳：
 * 深色背景下以品牌渐变光晕为视觉焦点，表单置于浮起的面板中。
 */
export function AuthShell({ title, subtitle, children }: AuthShellProps) {
  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden px-4 py-12">
      <div aria-hidden className="pointer-events-none absolute inset-0">
        <div className="absolute left-1/2 top-[-28%] h-[520px] w-[820px] -translate-x-1/2 rounded-full bg-primary/20 blur-[130px]" />
        <div className="absolute bottom-[-22%] right-[-8%] h-[380px] w-[380px] rounded-full bg-cyan-500/10 blur-[110px]" />
        <div className="absolute inset-0 bg-gradient-to-b from-transparent via-transparent to-background/60" />
      </div>

      <div className="relative w-full max-w-[400px]">
        <div className="mb-8 flex flex-col items-center gap-4 text-center">
          <BrandMark tileClassName="h-12 w-12 rounded-2xl text-base" />
          <div className="space-y-1.5">
            <h1 className="font-brand text-2xl font-semibold tracking-tight text-foreground">{title}</h1>
            {subtitle && <p className="text-sm text-muted-foreground">{subtitle}</p>}
          </div>
        </div>

        <div className="rounded-2xl border bg-card/70 p-6 shadow-[0_20px_60px_-20px_hsl(247_78%_66%/0.25)] backdrop-blur-xl sm:p-7">
          {children}
        </div>

        <p className="mt-8 text-center text-xs text-muted-foreground/70">
          hi Agent · 您的全方位 AI 代理平台
        </p>
      </div>
    </div>
  )
}
