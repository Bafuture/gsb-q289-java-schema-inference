# 数据类型推断与模式识别组件

Pair-wise GSB 标注任务仓库（第 17 批 / 289）。

| 项目 | 内容 |
|------|------|
| 任务类型 | Feature 迭代 |
| 任务难度 | 困难 |
| 语言/框架 | Java, Maven, JUnit 5 |
| 环境可复现等级 | 无外部依赖 |
| 构建方式 | Maven（含 mvnw wrapper，无需本机安装 Maven） |

> 本仓库是**初始环境快照**：只有工程骨架，不含任何实现代码。
> 分支说明：`main` 为初始环境；`A`、`B` 为两次独立执行各自的工作分支，均从 `main` 的同一个提交拉出。

## 运行方式

```bash
./mvnw -q verify
```

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们接入外部数据时没有 schema，需要从样本推断字段类型，而且不同批次的推断结果不能互相冲突。请从零实现一个数据类型推断与模式识别组件。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持从样本值推断字段类型（整数、小数、布尔、日期时间、字符串、枚举），并给出置信度；2) 支持类型冲突处理：同一字段出现多种类型时按优先级合并（如整数与小数合并为小数），无法合并要报告冲突；3) 支持枚举识别：取值种类少于阈值且重复率高时识别为枚举，阈值可配置；4) 支持空值与缺失处理：空值不应影响类型推断，需说明占比过高时的处理；5) 支持跨批次一致性：后续批次推断结果与既有模式不一致时，要能扩展模式或明确拒绝，不得静默改类型；6) 支持模式演进：识别到的类型从整数变为小数的场景要能平滑升级并保留历史；7) 提供统计：字段数、各类型分布、冲突字段数与枚举字段数；8) 测试覆盖各类型推断、冲突合并、枚举识别、空值处理、跨批次一致性与模式演进；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。

---

## 实现说明（分支 A）

组件位于 `com.example.gsb.schema` 包，零外部依赖：

| 类 | 职责 |
|----|------|
| `ValueTypeClassifier` | 单值分类：BOOLEAN → INTEGER → DECIMAL → DATETIME → STRING |
| `SchemaInferrer` | 单批次推断：类型合并、枚举识别、置信度、空值统计 |
| `SchemaMerger` | 跨批次合并：EXPAND 扩展 / STRICT 拒绝，绝不静默改类型 |
| `SchemaRegistry` | 门面：逐批摄入样本并维护累积模式 |
| `FieldProfile` / `TypeEvolution` | 字段模式与类型演进历史（含枚举快照） |
| `SchemaStats` | 字段数、类型分布、冲突字段数、枚举字段数 |

关键设计：

- **置信度** = 非空值占比；批内冲突时退化为“主导类型占比 × 非空占比”。
- **类型合并**：同类型直接合并；INTEGER+DECIMAL → DECIMAL；其余组合报 CONFLICT。
- **枚举识别**：`distinct < enumMaxDistinctValues` 且 `repetition ≥ enumMinRepetitionRate`（默认 20 / 0.5，可配置）。
- **空值处理**：null、空白、缺失键统一计为空值，不参与类型判定；空值占比超过阈值（默认 50%）产生警告，全空字段标记 UNKNOWN，后续批次可再定型。
- **模式演进**：INTEGER → DECIMAL、ENUM → STRING 平滑升级并写入 `typeHistory`；无法合并时 EXPAND 标记 CONFLICT、STRICT 明确拒绝并保留既有类型。

```java
SchemaRegistry registry = new SchemaRegistry(InferenceConfig.DEFAULT, MergePolicy.EXPAND);
SchemaMergeResult r1 = registry.ingest(rows1);
SchemaMergeResult r2 = registry.ingest(rows2);   // 自动演进或报告冲突
SchemaStats stats = registry.stats();
```
