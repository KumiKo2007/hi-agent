import { afterEach, expect, it, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { readFileSync } from "node:fs"
import { resolve } from "node:path"
import { ChatPanel } from "@/components/chat-panel"

const originalScrollIntoView = HTMLElement.prototype.scrollIntoView

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  if (originalScrollIntoView) {
    HTMLElement.prototype.scrollIntoView = originalScrollIntoView
  } else {
    delete (HTMLElement.prototype as Partial<HTMLElement>).scrollIntoView
  }
})

it("should_sendOneMessageFromTheWebPanelAndDisplayTheCompletedSseReply", async () => {
  HTMLElement.prototype.scrollIntoView = vi.fn()
  const fixtureEvents = JSON.parse(readFileSync(
    resolve(process.cwd(), "../AgentX/src/test/resources/contracts/chat-sse.json"), "utf8",
  )) as Array<{ content: string; done: boolean; messageType: string }>
  const fixture = fixtureEvents.map((event) => `data:${JSON.stringify(event)}\n\n`).join("")
  const splitAt = fixture.indexOf("\n\n") + 1
  expect(splitAt).toBeGreaterThan(1)

  const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith("/agents/sessions/regression-session/messages")) {
      return new Response(JSON.stringify({ code: 200, data: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    }
    if (url.endsWith("/agents/sessions/chat")) {
      expect(init?.method).toBe("POST")
      const encoder = new TextEncoder()
      const responseBody = new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(encoder.encode(fixture.slice(0, splitAt)))
          controller.enqueue(encoder.encode(fixture.slice(splitAt)))
          controller.close()
        },
      })
      return new Response(responseBody, {
        status: 200,
        headers: { "Content-Type": "text/event-stream" },
      })
    }
    throw new Error(`意外的请求：${url}`)
  })
  vi.stubGlobal("fetch", fetchMock)

  render(<ChatPanel conversationId="regression-session" />)
  await screen.findByText("暂无消息，开始发送消息吧")

  fireEvent.change(screen.getByPlaceholderText("输入消息...(Shift+Enter换行, Enter发送)"), {
    target: { value: "你好" },
  })
  fireEvent.click(screen.getByTitle("发送消息"))

  await screen.findByText("这是一条确定性回复")
  await waitFor(() => expect(screen.queryByText("思考中...")).toBeNull())
  await screen.findByTitle("发送消息")
  expect(screen.queryByTitle("中断对话")).toBeNull()

  const chatCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith("/agents/sessions/chat"))
  expect(chatCall).toBeDefined()
  expect(String(chatCall?.[0])).toBe("http://localhost:8088/api/agents/sessions/chat")
  expect(chatCall?.[1]?.method).toBe("POST")
  expect(JSON.parse(String(chatCall?.[1]?.body))).toEqual({
    sessionId: "regression-session",
    message: "你好",
  })
})
