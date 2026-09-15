# RAG 索引失败排查

## 核心结论

RAG 索引失败通常来自 Embedding 模型、统一分发渠道、Milvus VectorStore、collection schema 或 metadata 类型不匹配。系统不能伪造向量来让索引看似成功；没有可用 Embedding 模型或 Milvus 不可用时，应明确提示知识库暂不可用，普通 Copilot 对话和只读工具诊断仍可继续。

## 适用场景

本文用于处理知识库上传后 FAILED、重建失败、`no available channel for embedding model`、VectorStore 尚未配置、collection 维度不一致、`doc_id` 类型错误和 metadata filter 不生效等问题。阶段 4 只做平台公共知识库，不做普通用户私有文档。

## 配置/操作步骤

先确认后端配置 `aicrediflux.rag.embedding-model` 指向一个真实 Embedding 模型，例如 `Qwen/Qwen3-Embedding-0.6B`。再确认该模型存在于模型管理、渠道 models、abilities 和模型定价。然后启动 Milvus、etcd、MinIO，并检查 `spring.ai.vectorstore.milvus.collection-name`、host、port、embedding-dimension。首次更换 embedding 模型或维度时，建议新建 collection，避免旧向量维度和新模型输出维度冲突。最后重建文档索引，观察 chunk 数、indexedAt 和错误信息。

## 常见错误与排查顺序

`no available channel` 表示统一分发没有找到可用于 Embedding 的渠道，先查模型名称、group、models、abilities 和定价。Milvus 未启动或 VectorStore 未配置时，索引任务应失败并提示知识库暂不可用。向量维度不一致会导致写入 Milvus 失败，常见于把 1024 维 collection 换成 768 维模型。metadata 字段类型不匹配会导致写入或过滤失败，例如 `doc_id` 必须是字符串且长度小于 max_length，不能传数字或超长值。Milvus collection schema、embedding-dimension、doc_id、sourceName、space 和 locator 都要保持稳定。

## 与其他文档的边界

本文讲 Milvus、Embedding、VectorStore 和索引错误。SiliconFlow 模型接入见 `siliconflow-onboarding`；RAG_INDEX 和 RAG_QUERY 计费见 `ai-credit-billing`；SSE 引用展示见 `agent-run-sse`。

## Golden Set 覆盖锚点

RAG 索引失败原因包括 Embedding 模型不可用、Milvus 未启动、VectorStore 未配置、collection 维度不一致、metadata 类型错误和 no available channel。更换 embedding 模型后建议新建 collection。不能伪造向量。知识库失败时应明确降级，普通聊天和工具诊断继续可用。metadata filter 应能按 space 检索。

## 关键词与别名

RAG 索引失败、Milvus、Embedding、VectorStore、collection、embedding-dimension、metadata、doc_id、sourceName、space、locator、no available channel、知识库暂不可用、伪造向量。
