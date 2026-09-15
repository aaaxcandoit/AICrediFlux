# AICrediFlux 评测脚本使用说明

评测流程只保留三步：先跑 Agent/RAG，先跑 JMeter，再用总 Eval 汇总已有结果生成报告。

## 输出文件

只需要重点看这三个结果：

```text
scripts/agent/results/agent-summary.json      Agent/RAG 全量评测结果
docs/eval/reports/jmeter-summary.json         JMeter 压测汇总结果
docs/eval/reports/eval-report-*.md/json       最终总报告
```

JMeter 的原始文件仍会放在 `scripts/jmeter/results/`，用于追溯：

```text
scripts/jmeter/results/result-100.jtl
scripts/jmeter/results/result-500.jtl
scripts/jmeter/results/result-1000.jtl
scripts/jmeter/results/report-100/
scripts/jmeter/results/report-500/
scripts/jmeter/results/report-1000/
```

## 1. Agent/RAG 评测

前提：后端、MySQL、Redis、Milvus 已启动；Embedding 模型和聊天模型可用；环境变量里有 Web 登录 token。

```powershell
$env:AICREDIFLUX_EVAL_USER_TOKEN = '<普通用户 Web 登录 token>'
$env:AICREDIFLUX_EVAL_ADMIN_TOKEN = '<root/admin Web 登录 token>'
$env:AICREDIFLUX_EVAL_MODEL = 'deepseek-v4-flash'

powershell -ExecutionPolicy Bypass -File .\scripts\agent-eval.ps1 -SeedRag
```

输出：

```text
scripts/agent/results/agent-summary.json
```

## 2. JMeter 秒杀压测

建议一档一档跑，最稳，也方便定位库存和订单结果。

100 并发：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\jmeter-eval.ps1 `
  -CampaignIds 17 `
  -ThreadCounts 100 `
  -RampSeconds 2
```

500 并发：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\jmeter-eval.ps1 `
  -CampaignIds 18 `
  -ThreadCounts 500 `
  -RampSeconds 5
```

1000 并发：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\jmeter-eval.ps1 `
  -CampaignIds 19 `
  -ThreadCounts 1000 `
  -RampSeconds 10
```

输出：

```text
docs/eval/reports/jmeter-summary.json
```

压测前请确认：活动已发布并预热、活动 ID 正确、`scripts/jmeter/users.csv` 里有足够多的用户 token。

## 3. 使用已有结果汇总

如果你已经单独跑过 JMeter 和 Agent/RAG，总 Eval 只需要读取已有 JSON 并生成最终报告：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\eval.ps1
```

默认读取：

```text
scripts/agent/results/agent-summary.json
docs/eval/reports/jmeter-summary.json
```

如果结果文件放在别的位置，再显式指定：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\eval.ps1 `
  -JMeterResult docs/eval/reports/jmeter-summary.json `
  -AgentResult scripts/agent/results/agent-summary.json
```

最终输出：

```text
docs/eval/reports/eval-report-YYYYMMDD-HHmm.md
docs/eval/reports/eval-report-YYYYMMDD-HHmm.json
```

## 常见检查

- Agent 指标为空：先重新跑 `scripts/agent-eval.ps1 -SeedRag`，确认 `agent-summary.json` 里 `agent.summary.status` 是 `measured`。
- JMeter 指标为空：先重新跑 `scripts/jmeter-eval.ps1`，确认 `jmeter-summary.json` 里 `status` 是 `measured`。
- 压测成功但前端库存没变：确认命令里的 `CampaignIds` 是前端活动真实 ID，不是活动名称。
