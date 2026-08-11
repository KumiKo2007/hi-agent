"use client"

import React, { useState } from 'react';
import { ChevronDown, ChevronRight, CheckCircle, Circle, Clock, FileText, AlertCircle } from 'lucide-react';
import { Badge } from "@/components/ui/badge";
import { cn } from '@/lib/utils';

// 定义任务状态映射
const statusMapping = {
  'COMPLETED': 'completed',
  'IN_PROGRESS': 'in-progress',
  'WAITING': 'pending',
  'FAILED': 'failed'
} as const;

// 定义任务状态类型
type TaskStatus = 'completed' | 'in-progress' | 'pending' | 'failed';

// 任务接口
interface Task {
  id: string;
  title: string;
  status: TaskStatus;
}

// 任务DTO接口与后端一致
interface TaskDTO {
  id: string;
  taskName: string;
  status: string;
  progress: number;
  parentTaskId: string;
  taskResult?: string;
  startTime?: string;
  endTime?: string;
}

interface CurrentTaskListProps {
  taskName?: string;
  tasks?: TaskDTO[];
  isLoading?: boolean;
}

export function CurrentTaskList({ 
  taskName = "任务处理中...", 
  tasks = [], 
  isLoading = false 
}: CurrentTaskListProps) {
  // 默认设置为关闭状态(true表示已折叠)
  const [isCollapsed, setIsCollapsed] = useState(false);

  // 添加调试日志
 
  if (tasks.length > 0) {
 
  }

  // 转换任务格式
  const formattedTasks: Task[] = tasks.map(task => {
    // 计算正确的状态，优先使用status字段，也考虑progress值
    let derivedStatus: TaskStatus = 'pending';
    
    // 状态判断逻辑，按优先级处理
    if (task.status === 'COMPLETED' || task.progress >= 100) {
      derivedStatus = 'completed';
    } else if (task.status === 'IN_PROGRESS') {
      derivedStatus = 'in-progress';
    } else if (task.status === 'FAILED') {
      derivedStatus = 'failed';
    } else if (task.status === 'WAITING') {
      derivedStatus = 'pending';
    }
    
    // 记录状态转换日志
 
    
    return {
      id: task.id,
      title: task.taskName,
      status: derivedStatus
    };
  });

  // 计算各状态任务数量
  const completedCount = formattedTasks.filter(task => task.status === 'completed').length;
  const inProgressCount = formattedTasks.filter(task => task.status === 'in-progress').length;
  const pendingCount = formattedTasks.filter(task => task.status === 'pending').length;
  const failedCount = formattedTasks.filter(task => task.status === 'failed').length;

  // 根据任务状态获取相应的图标
  const getStatusIcon = (status: TaskStatus) => {
    switch (status) {
      case 'completed':
        return <CheckCircle className="h-4 w-4 text-green-400" />;
      case 'in-progress':
        return <Clock className="h-4 w-4 text-primary animate-pulse" />;
      case 'pending':
        return <Circle className="h-4 w-4 text-muted-foreground" />;
      case 'failed':
        return <AlertCircle className="h-4 w-4 text-red-400" />;
    }
  };

  // 根据任务状态获取相应的标签
  const getStatusBadge = (status: TaskStatus) => {
    switch (status) {
      case 'completed':
        return <Badge variant="outline" className="bg-green-500/10 text-green-400 border-green-500/40">已完成</Badge>;
      case 'in-progress':
        return <Badge variant="outline" className="bg-primary/10 text-primary border-primary/40">进行中</Badge>;
      case 'pending':
        return <Badge variant="outline" className="bg-muted/40 text-foreground/80 border-border">待处理</Badge>;
      case 'failed':
        return <Badge variant="outline" className="bg-red-500/10 text-red-400 border-red-500/40">失败</Badge>;
    }
  };

  if (isLoading) {
    return (
      <div className="rounded-lg border shadow-sm w-full p-4">
        <div className="animate-pulse flex space-x-4">
          <div className="flex-1 space-y-4 py-1">
            <div className="h-4 bg-muted rounded w-3/4"></div>
            <div className="h-4 bg-muted rounded w-1/2"></div>
          </div>
        </div>
      </div>
    );
  }

  if (!tasks || tasks.length === 0) {
    return null;
  }

  return (
    <div className="rounded-lg border shadow-sm w-full">
      {/* 任务名称 */}
      <div className="bg-primary/10 p-3 rounded-t-lg border-b border-primary/40 flex items-center">
        <FileText className="h-4 w-4 text-primary mr-2" />
        <h3 className="font-medium text-primary">{taskName}</h3>
      </div>
      
      {/* 标题栏 */}
      <div 
        className="flex items-center justify-between p-2 bg-muted/40 cursor-pointer border-b"
        onClick={() => setIsCollapsed(!isCollapsed)}
      >
        <div className="flex items-center space-x-2 overflow-x-auto">
          {isCollapsed ? <ChevronRight className="h-4 w-4 flex-shrink-0" /> : <ChevronDown className="h-4 w-4 flex-shrink-0" />}
          <span className="font-medium whitespace-nowrap">子任务列表</span>
          <div className="flex space-x-2 ml-2 flex-wrap">
            <Badge variant="outline" className="bg-muted/40 whitespace-nowrap">
              总计: {formattedTasks.length}
            </Badge>
            {completedCount > 0 && (
              <Badge variant="outline" className="bg-green-500/10 text-green-400 border-green-500/40 whitespace-nowrap">
                已完成: {completedCount}
              </Badge>
            )}
            {inProgressCount > 0 && (
              <Badge variant="outline" className="bg-primary/10 text-primary border-primary/40 whitespace-nowrap">
                进行中: {inProgressCount}
              </Badge>
            )}
            {pendingCount > 0 && (
              <Badge variant="outline" className="bg-muted/40 text-foreground/80 border-border whitespace-nowrap">
                待处理: {pendingCount}
              </Badge>
            )}
            {failedCount > 0 && (
              <Badge variant="outline" className="bg-red-500/10 text-red-400 border-red-500/40 whitespace-nowrap">
                失败: {failedCount}
              </Badge>
            )}
          </div>
        </div>
      </div>
      
      {/* 任务列表 */}
      {!isCollapsed && (
        <div 
          className="max-h-[250px] overflow-y-auto bg-card rounded-b-lg"
        >
          {formattedTasks.map((task) => (
            <div 
              key={task.id}
              className="flex items-center justify-between p-2 border-t hover:bg-muted/40"
            >
              <div className="flex items-center space-x-2 min-w-0 flex-1">
                <span className="flex-shrink-0">{getStatusIcon(task.status)}</span>
                <span className={cn(
                  "text-sm truncate",
                  task.status === 'completed' && "text-muted-foreground line-through"
                )}>
                  {task.title}
                </span>
              </div>
              <div className="flex items-center space-x-2 flex-shrink-0">
                {getStatusBadge(task.status)}
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
} 