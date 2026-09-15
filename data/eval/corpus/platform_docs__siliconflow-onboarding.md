# SiliconFlow 接入指南

## 核心结论

SiliconFlow 在 AICrediFlux 中按 OpenAI 兼容供应商接入。渠道的 Base URL 只填写统一前缀 `https://api.siliconflow.cn/v1`，不要填写 `https://api.siliconflow.cn/v1/embedding`、`/chat/completions` 或某个具体接口路径。平台的 Provider Adapter 会根据请求类型拼接 `/chat/completions`、`/embeddings` 或 `/models`，如果 Base URL 写到具体接口，模型列表同步和 Embedding 调用都会出现路径重复或接口不匹配。

## 适用场景

本文适用于新增 SiliconFlow 聊天模型、Embedding 模型和 RAG 知识库索引。常见模型包括聊天模型、视觉模型，以及 Embedding 模型 `Qwen/Qwen3-Embedding-0.6B`。Embedding 模型应配置在模型管理、渠道模型列表、模型能力 abilities 和 RAG 后端配置中，名称必须保持完全一致，包括斜杠、大小写和连字符。

## 配置/操作步骤

先在渠道管理新增渠道，供应商选择 OpenAI 兼容，名称可填 SiliconFlow，Base URL 填 `https://api.siliconflow.cn/v1`，上游 API Key 只保存在后端渠道配置里，不出现在工具结果、SSE 或日志明文。然后在模型管理添加 `Qwen/Qwen3-Embedding-0.6B`，供应商可使用 OpenAI 兼容渠道，启用状态打开，匹配类型一般选择 Exact Match。接着确认渠道的 `models` 字段包含同名模型，渠道 `group` 包含 `default` 或目标分组。最后确认模型能力 abilities 中存在 Embedding 能力，并在后端设置 `aicrediflux.rag.embedding-model=Qwen/Qwen3-Embedding-0.6B`。

## 常见错误与排查顺序

如果拉取不到上游模型列表，优先检查 Base URL 是否错误写成 `/v1/embedding`。模型列表通常来自 `/v1/models`，不是 Embedding 接口。如果 RAG 索引出现 `no available channel`，依次检查模型名称一致、渠道 `models`、渠道启用状态、渠道分组、模型定价、abilities 能力记录。供应商下拉没有 SiliconFlow 并不代表不可用，SiliconFlow 可以作为 OpenAI 兼容渠道使用；如果前端供应商枚举未列出 SiliconFlow，仍要保证渠道和模型指向兼容 Provider。

## 与其他文档的边界

本文只讲 SiliconFlow 接入、Base URL、模型名称和能力配置。AI Credit 扣费口径见 `ai-credit-billing`，渠道 priority、weight 和路由细节见 `channel-routing`，Milvus、VectorStore、collection 和 embedding-dimension 故障见 `rag-index-failures`。

## Golden Set 覆盖锚点

Base URL 必须是 `https://api.siliconflow.cn/v1`。不要填 `/v1/embedding`。`Qwen/Qwen3-Embedding-0.6B` 必须同时存在于模型管理、渠道 models、abilities 和 RAG embedding-model。模型名称大小写不一致会导致渠道选择失败。接入后要执行渠道测试，确认 Embedding 成功，再保存并索引知识库文档。API Key 可以作为概念出现，但真实密钥必须脱敏，不输出到 Copilot。

## 关键词与别名

SiliconFlow、硅基流动、Base URL、OpenAI 兼容、`/v1/models`、`/v1/embedding`、`Qwen/Qwen3-Embedding-0.6B`、abilities、模型定价、渠道测试、no available channel、aicrediflux.rag.embedding-model。
