"use client";

import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { FileText } from 'lucide-react';
import type { DocumentSegment } from '@/types/rag-dataset';

interface DocumentSegmentCardProps {
  segment: DocumentSegment;
  onClick?: (segment: DocumentSegment) => void;
  isSelected?: boolean;
}

export function DocumentSegmentCard({ 
  segment, 
  onClick, 
  isSelected = false 
}: DocumentSegmentCardProps) {
  const handleClick = () => {
    onClick?.(segment);
  };

  // 格式化相似度分数为百分比
  const formatScore = (score: number) => {
    return `${Math.round(score * 100)}%`;
  };

  // 生成内容预览
  const getContentPreview = () => {
    if (segment.contentPreview) {
      return segment.contentPreview.length > 50 
        ? segment.contentPreview.substring(0, 50) + '...'
        : segment.contentPreview;
    }
    return '文档内容预览';
  };

  return (
    <Card 
      className={`p-3 w-full cursor-pointer transition-all duration-200 hover:shadow-md ${
        isSelected 
          ? 'ring-2 ring-primary bg-primary/10 dark:bg-blue-950/20' 
          : 'hover:bg-muted/40 dark:hover:bg-secondary/50'
      }`}
      onClick={handleClick}
    >
      <div className="flex items-center gap-3">
        {/* 文档图标 */}
        <div className="flex-shrink-0">
          <FileText className="h-4 w-4 text-muted-foreground dark:text-muted-foreground" />
        </div>
        
        {/* 文档信息 */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-sm font-medium text-foreground dark:text-foreground/80">
              文档{segment.index || 1}
            </span>
          </div>
          
          {/* 文件名 */}
          <p className="text-xs text-muted-foreground dark:text-muted-foreground mt-1">
            来自: {segment.fileName}
          </p>
        </div>
      </div>
    </Card>
  );
}