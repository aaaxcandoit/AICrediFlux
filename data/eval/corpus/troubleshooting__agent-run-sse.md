# Agent Run 与 SSE 事件协议

## 核心结论

Copilot Run 的生命周期通过数据库状态和 SSE 事件共同表达。每个 Run 只能有一个终态，新的成功终态是 `COMPLETED`，失败是 `FAILED`，用户主动停止是 `CANCELLED`。旧数据中的 `SUCCEEDED` 只做兼容读取，不作为新写入状态。前端应把 cancelled 显示为已取消，不当成普通错误。

## 适用场景

本文用于解释右侧 Copilot Dock 中的运行状态、增量回答、工具轨迹、RAG 引用、usage 消耗、停止生成、断线恢复和终态唯一性。它也用于评测 SSE 顺序、断线重连和取消响应时间。

## 配置/操作步骤

提交消息后，后端创建 RUNNING Run 并发送 `status` 或 `run_started`。模型生成文本时输出 `text_delta`。Spring AI 触发工具前发送 `tool_start`，工具结束后发送 `tool_result`，状态可以是 SUCCEEDED、FAILED 或 DENIED。RAG 检索到引用时发送 `rag_refs`，包含 citationId、title、space、docNo、docId、sourceName、locator、snippet、score。模型调用结算后发送 `usage`，包含 promptTokens、completionTokens 和 AI Credit quota。最后发送 `done`，并带 `COMPLETED`、`FAILED` 或 `CANCELLED`。

## 常见错误与排查顺序

如果前端只看到工具轨迹却看不到最终回答，检查 `text_delta` 是否被工具区域遮挡或 Markdown 未渲染。若停止生成后仍继续调用工具，检查 stopRequested 和后续 ToolCallback 是否被阻止。连接中断时前端可自动重连一次，失败后显示“连接中断，可刷新会话或重新发送”，不要丢弃已有回答。每个 Run 如果出现多个 done 或 done 后又出现 error，就违反终态唯一性。已经发生的模型调用即使之后取消，也按实际 usage 结算。

## 与其他文档的边界

本文讲 Run 状态和 SSE 事件。工具权限和工具成功率见 Agent 评测；RAG 索引失败见 `rag-index-failures`；AI Credit 对账见 `ai-credit-billing`。

## Golden Set 覆盖锚点

主要事件包括 `status`、`text_delta`、`tool_start`、`tool_result`、`rag_refs`、`usage`、`done`、`error`、`cancelled`。主要终态包括 `COMPLETED`、`FAILED`、`CANCELLED`。`text_delta` 表示 assistant 增量文本。`tool_start` 表示开始调用工具，`tool_result` 表示工具完成、失败或权限拒绝。`rag_refs` 展示引用来源。`usage` 展示 promptTokens、completionTokens 和 AI Credit 消耗。取消不是普通错误，终态必须唯一。

## 关键词与别名

SSE、Run、status、text_delta、tool_start、tool_result、rag_refs、usage、done、error、cancelled、COMPLETED、FAILED、CANCELLED、终态唯一、停止生成、断线重连、AI Credit 消耗。
