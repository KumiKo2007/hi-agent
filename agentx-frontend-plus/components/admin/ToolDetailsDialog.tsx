"use client";

import React from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Separator } from "@/components/ui/separator";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Code, Github, ExternalLink, Calendar, User, Settings, Globe, Building, List, Wrench } from "lucide-react";
import { Tool, ToolStatus, getToolStatusText, getToolStatusColor } from "@/lib/admin-tool-service";

interface ToolDetailsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  tool: Tool | null;
}

export function ToolDetailsDialog({ open, onOpenChange, tool }: ToolDetailsDialogProps) {
  if (!tool) return null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[85vh] overflow-hidden">
        <DialogHeader>
          <DialogTitle className="flex items-center space-x-3">
            <div className="w-12 h-12 bg-muted rounded-lg flex items-center justify-center">
              {tool.icon ? (
                <img src={tool.icon} alt={tool.name} className="w-10 h-10 rounded" />
              ) : (
                <Code className="h-6 w-6 text-muted-foreground" />
              )}
            </div>
            <div>
              <h3 className="text-xl font-semibold">{tool.name}</h3>
              <p className="text-sm text-muted-foreground">{tool.description || "暂无描述"}</p>
            </div>
          </DialogTitle>
        </DialogHeader>

        <ScrollArea className="h-[70vh] pr-4">
          <div className="space-y-6">
          {/* 基本信息 */}
          <div className="space-y-4">
            <h4 className="text-sm font-medium text-foreground flex items-center">
              <Settings className="w-4 h-4 mr-2" />
              基本信息
            </h4>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-xs font-medium text-muted-foreground">工具类型</label>
                <div className="mt-1 flex items-center space-x-2">
                  <Badge variant={tool.isOffice ? "default" : "outline"}>
                    {tool.isOffice ? "官方" : "第三方"}
                  </Badge>
                  {tool.toolType && (
                    <span className="text-sm text-muted-foreground">{tool.toolType}</span>
                  )}
                </div>
              </div>
              <div>
                <label className="text-xs font-medium text-muted-foreground">部署范围</label>
                <div className="mt-1 flex items-center space-x-2">
                  <Globe className="w-4 h-4 text-muted-foreground" />
                  <span className="text-sm">{tool.isGlobal ? "全局工具" : "用户工具"}</span>
                </div>
              </div>
              <div>
                <label className="text-xs font-medium text-muted-foreground">审核状态</label>
                <div className="mt-1 space-y-1">
                  <Badge className={getToolStatusColor(tool.status)}>
                    {getToolStatusText(tool.status)}
                  </Badge>
                  {tool.rejectReason && tool.status === ToolStatus.FAILED && (
                    <div className="text-xs text-red-400">
                      拒绝原因: {tool.rejectReason}
                    </div>
                  )}
                </div>
              </div>
              <div>
                <label className="text-xs font-medium text-muted-foreground">创建时间</label>
                <div className="mt-1 flex items-center space-x-2">
                  <Calendar className="w-4 h-4 text-muted-foreground" />
                  <span className="text-sm">{new Date(tool.createdAt).toLocaleString('zh-CN')}</span>
                </div>
              </div>
            </div>
          </div>

          <Separator />

          {/* 创建者信息 */}
          <div className="space-y-4">
            <h4 className="text-sm font-medium text-foreground flex items-center">
              <User className="w-4 h-4 mr-2" />
              创建者信息
            </h4>
            <div className="flex items-center space-x-3">
              <Avatar className="h-10 w-10">
                <AvatarImage src={tool.userAvatarUrl} />
                <AvatarFallback>
                  {tool.userNickname?.charAt(0) || tool.userEmail?.charAt(0) || 'U'}
                </AvatarFallback>
              </Avatar>
              <div>
                <div className="font-medium">
                  {tool.userNickname || tool.userEmail || '未知用户'}
                </div>
                <div className="text-sm text-muted-foreground">{tool.userEmail}</div>
              </div>
            </div>
          </div>

          {/* 技术信息 */}
          {(tool.uploadUrl || tool.mcpServerName) && (
            <>
              <Separator />
              <div className="space-y-4">
                <h4 className="text-sm font-medium text-foreground flex items-center">
                  <Building className="w-4 h-4 mr-2" />
                  技术信息
                </h4>
                <div className="space-y-3">
                  {tool.uploadUrl && (
                    <div>
                      <label className="text-xs font-medium text-muted-foreground">源码地址</label>
                      <div className="mt-1">
                        <a 
                          href={tool.uploadUrl} 
                          target="_blank" 
                          rel="noopener noreferrer"
                          className="text-primary hover:text-primary/90 flex items-center gap-2 text-sm"
                        >
                          <Github className="w-4 h-4" />
                          <span>{tool.uploadUrl}</span>
                          <ExternalLink className="w-3 h-3" />
                        </a>
                      </div>
                    </div>
                  )}
                  {tool.mcpServerName && (
                    <div>
                      <label className="text-xs font-medium text-muted-foreground">MCP服务器名称</label>
                      <div className="mt-1 font-mono text-sm bg-muted px-2 py-1 rounded">
                        {tool.mcpServerName}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </>
          )}

          {/* 工具列表 */}
          {tool.toolList && tool.toolList.length > 0 && (
            <>
              <Separator />
              <div className="space-y-4">
                <h4 className="text-sm font-medium text-foreground flex items-center">
                  <List className="w-4 h-4 mr-2" />
                  工具列表 ({tool.toolList.length})
                </h4>
                <div className="grid grid-cols-1 gap-3">
                  {tool.toolList.map((toolItem: any, index: number) => (
                    <div key={index} className="p-4 bg-muted/40 rounded-lg border-l-4 border-primary/40">
                      <div className="flex items-start space-x-3">
                        <div className="w-8 h-8 bg-primary/15 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5">
                          <Wrench className="h-4 w-4 text-primary" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center justify-between">
                            <h5 className="text-sm font-medium text-foreground">
                              {toolItem.name || `工具 #${index + 1}`}
                            </h5>
                            {toolItem.type && (
                              <Badge variant="outline" className="text-xs">
                                {toolItem.type}
                              </Badge>
                            )}
                          </div>
                          {toolItem.description && (
                            <p className="mt-1 text-sm text-muted-foreground leading-relaxed">
                              {toolItem.description}
                            </p>
                          )}
                          {/* 显示工具的参数或输入输出 */}
                          {(toolItem.inputSchema || toolItem.parameters || toolItem.arguments) && (
                            <div className="mt-2 space-y-2">
                              <div className="text-xs font-medium text-muted-foreground">参数信息:</div>
                              <div className="bg-card p-2 rounded border text-xs font-mono">
                                <pre className="whitespace-pre-wrap text-foreground/80">
                                  {JSON.stringify(
                                    toolItem.inputSchema || toolItem.parameters || toolItem.arguments, 
                                    null, 
                                    2
                                  )}
                                </pre>
                              </div>
                            </div>
                          )}
                          {/* 显示其他属性 */}
                          {Object.keys(toolItem).length > 0 && (
                            <div className="mt-2 flex flex-wrap gap-1">
                              {Object.entries(toolItem).map(([key, value]) => {
                                if (key === 'name' || key === 'description' || key === 'type' || 
                                    key === 'inputSchema' || key === 'parameters' || key === 'arguments') {
                                  return null;
                                }
                                if (typeof value === 'string' || typeof value === 'number') {
                                  return (
                                    <span key={key} className="inline-flex items-center px-2 py-1 rounded-full text-xs bg-muted text-foreground/80">
                                      <span className="font-medium">{key}:</span>
                                      <span className="ml-1">{String(value)}</span>
                                    </span>
                                  );
                                }
                                return null;
                              })}
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}

          {/* 完整描述 */}
          {tool.description && (
            <>
              <Separator />
              <div className="space-y-4">
                <h4 className="text-sm font-medium text-foreground">完整描述</h4>
                <div className="text-sm text-foreground/80 bg-muted/40 p-3 rounded-lg">
                  {tool.description}
                </div>
              </div>
            </>
          )}
          </div>
        </ScrollArea>
      </DialogContent>
    </Dialog>
  );
}