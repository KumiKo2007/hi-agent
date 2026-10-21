"use client"

import { useState, useEffect } from "react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { Bot, Database, FileText, GitPullRequest, LogOut, Menu, Settings } from "lucide-react"
import { toast } from "@/hooks/use-toast"

import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet"
import { deleteCookie } from "@/lib/utils"
import { getUserInfoWithToast, type UserInfo } from "@/lib/user-service"
import { BrandMark } from "@/components/brand-mark"

const navItems = [
  { name: "教学准备", href: "/teaching", icon: FileText },
  { name: "代码审查", href: "/reviews", icon: GitPullRequest },
  {
    name: "工作区",
    href: "/workspace",
    icon: Bot,
  },
  {
    name: "工作室",
    href: "/studio",
    icon: FileText,
  },
  {
    name: "知识库",
    href: "/knowledge",
    icon: Database,
  },
]

export function NavigationBar() {
  const pathname = usePathname()
  const router = useRouter()
  const [open, setOpen] = useState(false)
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null)
  const [loading, setLoading] = useState(true)

  // 获取用户信息
  useEffect(() => {
    async function fetchUserInfo() {
      try {
        setLoading(true)
        const response = await getUserInfoWithToast()
        
        if (response.code === 200) {
          setUserInfo(response.data)
        } else {
 
        }
      } catch {
 
      } finally {
        setLoading(false)
      }
    }

    fetchUserInfo()
  }, [])

  // Check if current path matches the menu item's href
  const isActiveRoute = (href: string) => {
    return pathname === href || pathname.startsWith(`${href}/`)
  }

  // 获取用户头像首字母
  const getUserAvatarFallback = () => {
    if (!userInfo?.nickname) return "U"
    return userInfo.nickname.charAt(0).toUpperCase()
  }

  const handleLogout = () => {
    // 清除localStorage中的token
    localStorage.removeItem("auth_token")
    
    // 清除cookie中的token
    deleteCookie("token")
    
    // 显示退出成功提示
    toast({
      title: "成功",
      description: "退出登录成功"
    })
    
    // 跳转到登录页
    router.push("/login")
  }

  return (
    <header className="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="container flex h-14 items-center px-4">
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetTrigger asChild>
            <Button variant="ghost" size="icon" className="mr-2 md:hidden">
              <Menu className="h-5 w-5" />
              <span className="sr-only">Toggle Menu</span>
            </Button>
          </SheetTrigger>
          <SheetContent side="left" className="pr-0">
            <div className="px-7">
              <Link href="/" className="flex items-center" onClick={() => setOpen(false)}>
                <BrandMark />
              </Link>
            </div>
            <nav className="mt-6 flex flex-col gap-4 px-2">
              {navItems.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={() => setOpen(false)}
                  className={cn(
                    "flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium hover:bg-accent hover:text-accent-foreground",
                    isActiveRoute(item.href) ? "bg-accent text-accent-foreground" : "transparent",
                  )}
                >
                  <item.icon className="h-5 w-5" />
                  {item.name}
                </Link>
              ))}
            </nav>
          </SheetContent>
        </Sheet>
        <Link href="/" className="mr-6 flex items-center space-x-2">
          <BrandMark />
        </Link>
        <div className="flex flex-1 items-center justify-between">
          <nav className="flex items-center space-x-6">
            {navItems.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-1 text-sm font-medium transition-colors",
                  isActiveRoute(item.href)
                    ? "text-primary font-semibold"
                    : "text-foreground/60 hover:text-foreground/80",
                )}
              >
                <item.icon className="h-5 w-5" />
                {item.name}
              </Link>
            ))}
          </nav>
          <div className="flex items-center gap-2">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" className="rounded-full">
                  <Avatar className="h-8 w-8">
                    <AvatarImage src="/placeholder.svg?height=32&width=32" alt="User" />
                    <AvatarFallback>
                      {loading ? "..." : getUserAvatarFallback()}
                    </AvatarFallback>
                  </Avatar>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuLabel className="flex items-center gap-2">
                  <Avatar className="h-8 w-8">
                    <AvatarImage src="/placeholder.svg?height=32&width=32" alt="User" />
                    <AvatarFallback>
                      {loading ? "..." : getUserAvatarFallback()}
                    </AvatarFallback>
                  </Avatar>
                  <div>
                    <div className="font-medium">
                      {loading ? "加载中..." : (userInfo?.nickname || "未知用户")}
                    </div>
                    {userInfo?.email && (
                      <div className="text-sm text-muted-foreground">
                        {userInfo.email}
                      </div>
                    )}
                  </div>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild>
                  <Link href="/settings/github">
                    <Settings className="mr-2 h-4 w-4" />
                    GitHub 连接
                  </Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild>
                  <Link href="/settings/providers">
                    <Settings className="mr-2 h-4 w-4" />
                    服务提供商
                  </Link>
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={handleLogout}>
                  <LogOut className="mr-2 h-4 w-4" />
                  退出登录
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </div>
    </header>
  )
}
