# DeepSeek 模型选择指南

## 核心结论

DeepSeek 模型在 AICrediFlux 中是平台模型配置，不代表供应商天然可用。用户能否使用 `deepseek-v4-flash`、`deepseek-v4-pro` 或视觉实验模型，取决于模型管理、渠道 models、abilities、定价快照、用户 group 和渠道健康度。模型推荐要同时考虑任务复杂度、AI Credit 成本、响应速度和当前可用渠道。

## 适用场景

`deepseek-v4-flash` 适合普通中文对话、平台说明、轻量代码问答、成本敏感问答和 Copilot 诊断总结。更强推理模型适合复杂代码设计、长链路排障、严肃方案比较和需要更高推理稳定性的任务。视觉模型只适合包含图片输入的场景，不能替代 Embedding 模型。

## 配置/操作步骤

选择模型前，先确认该模型在模型管理中启用，有模型倍率、补全倍率或模型价格；再确认渠道 models 包含同名模型且 group 匹配；最后看 ChannelStatus 的近期成功率、延迟、429 和 5xx 风险。成本估算通常需要模型倍率、completionRatio、groupRatio、prompt tokens、completion tokens 和历史用量。余额不足时应优先选择低倍率、低输出长度的模型。

## 常见错误与排查顺序

如果 DeepSeek 模型在 Copilot 下拉中不可选，先看模型是否启用，再看渠道是否包含该模型。若模型推荐只看能力，不看 AI Credit 余额和实时渠道，会出现“推荐了用户用不起或当前路由不可用的模型”。代码问答可优先考虑 `deepseek-v4-flash`，但涉及架构重构、并发一致性和跨模块设计时，可以建议切换更强推理模型。

## 与其他文档的边界

本文只说明 DeepSeek 模型选择和成本敏感策略。渠道路由见 `channel-routing`，AI Credit Billing 见 `ai-credit-billing`，SiliconFlow Embedding 模型配置见 `siliconflow-onboarding`。

## Golden Set 覆盖锚点

普通中文对话推荐 `deepseek-v4-flash`。成本敏感任务优先低倍率模型、控制 completion tokens，并结合 AI Credit 余额。更强推理模型适合复杂推理。DeepSeek 可用性依赖模型启用、渠道 models、abilities、定价、group 和渠道健康度。模型推荐不能只看能力，还要看成本、余额、延迟和路由可用性。

## 关键词与别名

DeepSeek、deepseek-v4-flash、deepseek-v4-pro、模型推荐、成本敏感、AI Credit 余额、completionRatio、groupRatio、模型倍率、补全倍率、代码问答、强推理模型。
