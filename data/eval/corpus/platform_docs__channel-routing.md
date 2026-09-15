# 渠道路由与模型匹配

## 核心结论

AICrediFlux 的模型请求必须经过统一渠道路由。路由依据 group、model、渠道启用状态、模型列表、priority、weight 和模型能力综合选择 Channel。模型名称使用 Exact Match 时必须精确匹配，不能把供应商显示名、别名或大小写不同的名称混用。

## 适用场景

本文用于排查 no available channel、渠道模型列表和 abilities 不一致、同优先级流量分配、管理员渠道诊断，以及新增模型后的可用性问题。Copilot 只给出诊断建议，不会自动修改渠道 priority、weight、倍率、用户额度或套餐配置。

## 配置/操作步骤

新增模型后，先在模型管理保存模型名称和定价，再把同名模型加入渠道 `models` 列表，确认渠道 group 包含用户所属分组，例如 `default`。渠道状态必须启用。多个渠道可用时，priority 决定优先级，数值更优的渠道先进入候选；同一 priority 下按 weight 做加权分配。abilities 用于说明模型能力，例如 chat、embedding、vision；如果模型在渠道 models 中存在但 abilities 缺 Embedding，RAG 索引仍可能无法选择该模型。

## 常见错误与排查顺序

出现 no available channel 时，按顺序检查：模型名称是否完全一致，渠道是否启用，group 是否匹配，models 是否包含目标模型，模型定价是否存在，abilities 是否包含所需能力，渠道 Base URL 和上游 Key 是否可用。渠道模型列表和 abilities 不一致会造成“前端看得到模型但后端无法用于某类任务”的错觉。不要为了临时通过测试随便改供应商模型名称，因为历史日志、价格快照、模型映射和评测数据都依赖稳定名称。

## 与其他文档的边界

本文讲路由、priority、weight、group、model、abilities。SiliconFlow 的 Base URL 和上游模型同步见 `siliconflow-onboarding`；AI Credit 计费对账见 `ai-credit-billing`；实时渠道成功率、延迟、429、5xx 可由管理员使用 ChannelStatus 工具查看。

## Golden Set 覆盖锚点

priority 表示渠道排序优先级，weight 表示同优先级渠道的流量权重。group 和 model 是选渠的第一层过滤条件。no available channel 不是模型不存在的唯一原因，也可能是 group、models、abilities、定价或状态不匹配。管理员诊断渠道不会自动改权重。渠道异常时 Copilot 第一版只输出建议，不直接修改配置。

## 关键词与别名

priority、weight、group、model、Channel、渠道路由、Exact Match、models、abilities、模型定价、no available channel、成功率、延迟、429、5xx。
