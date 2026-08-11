"use client"

import { Plus } from "lucide-react"
import { Button } from "@/components/ui/button"

interface EmptyStateProps {
  title: string
  description: string
  actionLabel?: string
  onAction?: () => void
}

export function EmptyState({ title, description, actionLabel, onAction }: EmptyStateProps) {
  return (
    <div className="text-center">
      <h2 className="text-xl font-semibold text-foreground/80 mb-2">{title}</h2>
      <p className="text-muted-foreground mb-4">{description}</p>
      {actionLabel && (
        <Button
          className="justify-center items-center gap-2 px-4 py-2 bg-gradient-to-r from-primary to-primary hover:from-primary hover:to-primary text-white rounded-full shadow-sm hover:shadow-md transform hover:scale-105 transition-all duration-200"
          onClick={onAction}
        >
          <Plus className="h-4 w-4" />
          {actionLabel}
        </Button>
      )}
    </div>
  )
}

