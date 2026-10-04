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

## 组件说明（本次实现）

包路径：`com.example.gsb.schemainfer`，入口为 `SchemaEngine`。

### 快速上手

```java
SchemaEngine engine = new SchemaEngine();               // 默认阈值
// 或自定义：new SchemaEngine(InferenceConfig.builder().enumMaxDistinct(10).build());

IngestReport r1 = engine.ingestBatch("batch-1", List.of(
        Map.of("age", "1", "price", "9.9"),
        Map.of("age", "2", "price", "19.9")));

IngestReport r2 = engine.ingestBatch("batch-2", List.of(
        Map.of("age", "3.5", "price", "29.9")));        // age: INTEGER -> DECIMAL，记录演进历史

engine.getSchema();            // 既有模式（每字段类型/枚举值/空值统计）
engine.getEvolutionHistory();  // 全部模式演进记录
engine.getStatistics();        // 字段数、类型分布、冲突字段数、枚举字段数
```

### 设计要点

- **类型推断**：单值按 布尔 → 整数 → 小数 → 日期时间 → 字符串 的优先级分类；
  字段级置信度 = 与主类型组一致的非空值占比（`FieldInference.getConfidence()`）。
- **冲突合并**：整数与小数同属数值组，合并为小数，不产生冲突；其余跨组混排
  （如整数 + 布尔）取占比最高的组为解析类型，并把少数派类型记入
  `getConflictingTypes()`，字段进入冲突名单。
- **枚举识别**：非空样本数 ≥ `enumMinSamples`、取值种类 ≤ `enumMaxDistinct`、
  重复率（1 − 种类数/非空数）≥ `enumMinRepetitionRatio` 时判定为枚举，
  阈值均可通过 `InferenceConfig` 配置。
- **空值处理**：`null`、空白串与可配置的空值 token（默认 `null`、`n/a`）不参与
  类型推断；缺失的键按空值计。空值占比 ≥ `highNullRatioThreshold`（默认 0.8）时
  字段被标记为 `mostlyNull`——类型仍由非空样本给出，但提示下游证据不足；
  全空字段推断为 `UNKNOWN`，且不会触发跨批次类型变更。
- **跨批次一致性**：每批次与既有模式逐字段对账——兼容则 `ACCEPTED`
  （如整数批次进小数字段、任意原子值进字符串字段），可扩展则 `EVOLVED`
  并写入演进历史，不兼容则 `REJECTED` 且模式保持不变，绝不静默改类型。
- **模式演进**：整数 → 小数平滑升级；`UNKNOWN` 字段由首个非空批次定型；
  枚举取值并集超出阈值时显式演进为基类型。所有演进均记录在
  `SchemaEvolution`（批次、字段、源/目标类型、原因、时间戳）。
