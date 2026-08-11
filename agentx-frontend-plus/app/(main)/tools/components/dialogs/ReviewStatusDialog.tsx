import { UserTool, ToolStatus } from "../../utils/types";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Check, Circle, Loader, AlertTriangle, Info } from "lucide-react";
import { cn } from "@/lib/utils";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

interface ReviewStatusDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  tool: UserTool | null;
}

// 审核状态顺序
const REVIEW_STEPS = [
  { status: ToolStatus.WAITING_REVIEW, label: "等待审核", description: "您的工具已提交，正在等待审核" },
  { status: ToolStatus.GITHUB_URL_VALIDATE, label: "验证GitHub URL", description: "正在验证GitHub仓库地址" },
  { status: ToolStatus.DEPLOYING, label: "部署中", description: "正在进行工具部署" },
  { status: ToolStatus.FETCHING_TOOLS, label: "获取工具列表", description: "正在获取工具功能列表" },
  { status: ToolStatus.MANUAL_REVIEW, label: "人工审核", description: "正在进行人工审核确认" },
  { status: ToolStatus.APPROVED, label: "审核通过", description: "恭喜！您的工具已经通过审核" }
];

// 获取当前状态在流程中的索引
function getStatusIndex(status: ToolStatus): number {
  const index = REVIEW_STEPS.findIndex(step => step.status === status);
  return index >= 0 ? index : 0;
}

export function ReviewStatusDialog({
  open,
  onOpenChange,
  tool
}: ReviewStatusDialogProps) {
  if (!tool) return null;
  
  // 判断工具当前的审核状态
  const statusIndex = getStatusIndex(tool.status);
  const isFailed = tool.status === ToolStatus.FAILED;
  
  // 获取失败的具体环节索引
  const failedStepIndex = isFailed && tool.failedStepStatus ? 
    getStatusIndex(tool.failedStepStatus) : -1;
  
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="text-center text-xl font-bold">审核状态</DialogTitle>
        </DialogHeader>
        
        <div className="py-4">
          {/* 工具基本信息 */}
          <div className="mb-6 flex items-center gap-3 bg-muted/10 p-3 rounded-lg border">
            <div className="flex h-10 w-10 items-center justify-center rounded-md bg-primary/10">
              {tool.icon ? (
                <img src={tool.icon} alt={tool.name} className="h-6 w-6" />
              ) : (
                <Info className="h-5 w-5 text-primary" />
              )}
            </div>
            <div>
              <h3 className="font-medium line-clamp-1">{tool.name}</h3>
              <p className="text-xs text-muted-foreground">{tool.subtitle}</p>
            </div>
          </div>
          
          {/* 失败状态提示 */}
          {isFailed && tool.rejectReason && (
            <Alert variant="destructive" className="mb-5">
              <AlertTriangle className="h-4 w-4" />
              <AlertTitle>审核未通过</AlertTitle>
              <AlertDescription>
                <ScrollArea className="max-h-[100px] mt-2">
                  <p className="text-sm">{tool.rejectReason}</p>
                </ScrollArea>
              </AlertDescription>
            </Alert>
          )}
          
          {/* 当前状态概述 */}
          <div className={cn(
            "mb-4 p-4 rounded-lg text-center",
            isFailed 
              ? "bg-red-500/10 text-red-400 border border-red-500/40" 
              : tool.status === ToolStatus.APPROVED
                ? "bg-green-500/10 text-green-400 border border-green-500/40"
                : "bg-primary/10 text-primary border border-primary/40"
          )}>
            <p className="font-semibold">
              {isFailed 
                ? `审核在"${tool.failedStepStatus 
                   ? (REVIEW_STEPS.find(s => s.status === tool.failedStepStatus)?.label || "未知")
                   : "未知"}"环节未通过` 
                : tool.status === ToolStatus.APPROVED
                  ? "恭喜，您的工具已通过审核！"
                  : `审核进度：${statusIndex + 1}/${REVIEW_STEPS.length}`
              }
            </p>
          </div>
          
          {/* 步骤指示器 */}
          <div className="space-y-0 relative">
            {/* 进度连接线 */}
            <div className="absolute left-4 top-4 bottom-4 w-[2px] bg-muted -z-10"></div>
            
            {REVIEW_STEPS.map((step, index) => {
              // 判断步骤状态
              const isPassed = !isFailed ? (index <= statusIndex) : 
                               (failedStepIndex >= 0 ? index < failedStepIndex : false);
              
              const isCurrent = !isFailed ? (index === statusIndex) : 
                                (failedStepIndex >= 0 ? index === failedStepIndex : false);
              
              const isUpcoming = !isFailed ? (index > statusIndex) : 
                                 (failedStepIndex >= 0 ? index > failedStepIndex : true);
              
              // 失败环节特殊处理
              const isFailedStep = isFailed && index === failedStepIndex;
              
              return (
                <div key={step.status} className={cn(
                  "flex items-start gap-4 py-3 pl-2 pr-4 rounded-lg",
                  isCurrent && "bg-primary/10",
                  isFailedStep && "bg-red-50/50"
                )}>
                  {/* 步骤图标 */}
                  <div className={cn(
                    "flex-shrink-0 h-8 w-8 flex items-center justify-center rounded-full z-10",
                    isPassed ? "bg-green-500/15 text-green-400 ring-2 ring-white" : 
                    isCurrent ? "bg-primary/15 text-primary ring-2 ring-white" :
                    isFailedStep ? "bg-red-500/15 text-red-400 ring-2 ring-white" :
                    "bg-muted text-muted-foreground ring-2 ring-white"
                  )}>
                    {isPassed && !isCurrent ? (
                      <Check className="h-4 w-4" />
                    ) : isCurrent ? (
                      <Loader className="h-4 w-4 animate-spin" />
                    ) : isFailedStep ? (
                      <AlertTriangle className="h-4 w-4" />
                    ) : (
                      <Circle className="h-4 w-4" />
                    )}
                  </div>
                  
                  {/* 步骤文字 */}
                  <div className="flex-1 min-w-0">
                    <p className={cn(
                      "font-semibold",
                      isPassed ? "text-green-400" : 
                      isCurrent ? "text-primary" :
                      isFailedStep ? "text-red-400" :
                      "text-muted-foreground"
                    )}>
                      {step.label}
                      {isFailedStep && " (失败)"}
                    </p>
                    
                    {/* 步骤描述 */}
                    <p className="text-xs text-muted-foreground mt-0.5">
                      {isFailedStep && tool.rejectReason 
                        ? "审核失败，请查看详细原因" 
                        : step.description}
                    </p>
                  </div>
                </div>
              );
            })}
            
            {/* 失败状态单独显示（只在没有具体失败环节时显示） */}
            {isFailed && failedStepIndex < 0 && (
              <div className="flex items-start gap-4 py-3 pl-2 pr-4 rounded-lg bg-red-50/50 mt-1">
                <div className="flex-shrink-0 h-8 w-8 flex items-center justify-center rounded-full bg-red-500/15 text-red-400 ring-2 ring-white z-10">
                  <AlertTriangle className="h-4 w-4" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="font-semibold text-red-400">审核未通过</p>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    您的工具未通过审核，请查看失败原因并修改后重新提交
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
        
        <DialogFooter>
          <Button 
            variant="outline" 
            onClick={() => onOpenChange(false)}
            className="w-full sm:w-auto"
          >
            关闭
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
} 