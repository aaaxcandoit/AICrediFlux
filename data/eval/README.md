# AICrediFlux Eval Data

阶段 6 评测数据统一放在 `data/eval`，不放入 `docs`。`docs/eval/reports` 只保存脚本生成的评测报告。

## 目录

```text
data/eval/golden/agent-golden-set.jsonl
data/eval/golden/rag-golden-set.jsonl
data/eval/corpus/*.md
```

## Agent Golden Set 字段

每行一个 JSON 对象：

- `id`：用例编号，稳定不复用。
- `category`：`direct`、`tool`、`rag`、`permission`、`error`、`cancel`。
- `role`：`user` 或 `admin`。
- `question`：发送给 Copilot 的问题。
- `expectedTools`：期望触发的工具名列表，普通问答为空数组。
- `expectedRag`：是否期望产生 `rag_refs`。
- `expectedKeywords`：最终答案应包含的关键词，用于规则判定。
- `forbiddenKeywords`：答案、工具摘要和引用中不应出现的敏感词。
- `expectPermissionDenied`：是否期望权限拒绝。
- `notes`：维护备注，不参与评测。

## RAG Golden Set 字段

每行一个 JSON 对象：

- `id`：用例编号。
- `space`：知识空间，当前为 `platform_docs`、`model_docs`、`troubleshooting`。
- `question`：检索问题。
- `topK`：目标评测 topK，脚本会同时计算 Recall@5、Recall@8、MRR@8。
- `expectedDocIds`：期望命中的语料文档 id，对应 `data/eval/corpus/{space}__{docId}.md`。
- `expectedKeywords`：引用片段或答案中应命中的关键词。
- `answerKeywords`：用于评估最终答案是否覆盖关键事实。

## 维护规则

- 新增 case 时只追加新 id，不修改既有 id 的语义。
- 修改语料内容后，保留文件名中的 docId，方便历史报告可比。
- 修改 RAG Golden Set 或 corpus 后，先运行 `node scripts/eval/validate-rag-corpus-coverage.mjs`，确保 expectedDocIds 和 expectedKeywords 均可覆盖。
- 指标必须来自脚本输出，不手填简历数字。
- 第一版使用规则判定，不引入 LLM-as-judge。
- `toolExecutionSuccessPct` 只统计期望成功执行的业务工具；权限拒绝类用例单独计入 `permissionBlockPct`。
