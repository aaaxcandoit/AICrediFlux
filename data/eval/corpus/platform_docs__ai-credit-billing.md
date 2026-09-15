# AI Credit Billing 口径

## 核心结论

AI Credit 是 AICrediFlux 对用户展示的唯一消费单位。模型 token 是输入输出明细，用于 Billing 计算；页面上的余额、购买、消耗、统计和 Copilot 本轮消耗都统一展示 AI Credit。不要在数字后重复写单位，标题或小字写清单位即可。

## 适用场景

本文适用于 API 调用、Playground、Copilot 普通对话、Spring AI Tool Calling 和 RAG Embedding 计费。真实模型调用、RAG_INDEX 索引 Embedding、RAG_QUERY 查询 Embedding 都必须进入统一模型分发和 Billing。只读业务工具本身不额外扣 AI Credit，因为工具没有调用上游模型，只读取钱包、价格、用量或渠道状态。

## 配置/操作步骤

每次模型调用通过 `ModelDispatchService` 选择渠道，生成 requestId，构造 DispatchContext，并写入调用日志。API/Playground 来源写对应 source；Copilot 最终回答写 `dispatch_source=AGENT`，并携带 sessionNo、runNo；管理员索引知识库时 Embedding 写 `dispatch_source=RAG_INDEX`，用户检索知识库时 Embedding 写 `dispatch_source=RAG_QUERY`。Run 结束时汇总 promptTokens、completionTokens 和 quota，assistant 消息也保存 usageJson。

## 常见错误与排查顺序

如果钱包扣减和日志对不上，先查同一个 requestId、runNo 和 sessionNo 是否只有一次真实模型调用；再查调用日志 quota、Run usage、钱包流水是否一致。失败路径要确认预扣是否退款。若只读工具导致余额变化，说明工具被错误接入了 Billing。若 RAG 索引失败但已经扣费，需要查看 Embedding 调用是否成功、后续 Milvus 写入是否失败，以及补偿策略是否记录清楚。

## 与其他文档的边界

本文说明计费和对账。模型选择、DeepSeek 成本敏感场景见 `deepseek-models`；渠道 priority、weight 和 no available channel 见 `channel-routing`；RAG 索引不可用和 VectorStore 故障见 `rag-index-failures`。

## Golden Set 覆盖锚点

AI Credit 是展示单位。工具调用本身不重复扣费。AGENT 日志代表 Copilot 模型回答。RAG_INDEX 计入发起索引的管理员，RAG_QUERY 计入当前提问用户。Billing 使用 prompt tokens、completion tokens、模型倍率、补全倍率、分组倍率或模型价格换算 quota。对账要同时看 Run usage、调用日志 quota 和钱包扣减。模型 token 不等于 AI Credit，但 token 是计算 AI Credit 的基础。

## 关键词与别名

AI Credit、Billing、dispatch_source=AGENT、RAG_INDEX、RAG_QUERY、promptTokens、completionTokens、quota、Run usage、钱包扣减、调用日志、工具不扣费、失败退款。
