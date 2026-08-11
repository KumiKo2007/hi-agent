import { cn } from "@/lib/utils"

type BrandMarkProps = {
  className?: string
  tileClassName?: string
  showAgent?: boolean
}

/**
 * hi Agent 品牌标识：
 * 左侧为极光渐变的圆角方块，内嵌白色 "hi" 字形，
 * 右侧为 "Agent" 字标。整体读作 "hi Agent"。
 */
export function BrandMark({ className, tileClassName, showAgent = true }: BrandMarkProps) {
  return (
    <span className={cn("inline-flex items-center gap-2.5", className)}>
      <span
        aria-hidden
        className={cn(
          "brand-gradient inline-flex h-8 w-8 items-center justify-center rounded-xl text-white brand-glow",
          tileClassName,
        )}
      >
        <span className="font-brand text-[13px] font-bold leading-none tracking-tight">hi</span>
      </span>
      {showAgent && (
        <span className="font-brand text-[17px] font-semibold tracking-tight text-foreground">
          Agent
        </span>
      )}
    </span>
  )
}
