> 2026-10-27 后续更正：用户已明确授权扩大到当前项目目录内现有Java文件。以下是更正前的原因追溯；当前实现已移除定位/应用的Sources Root要求，保留项目canonical边界。

# F2 Sources Root 限制追溯

2026-10-27，dev-manager；按用户要求查原因，本项不扩大编辑范围。

## 实际原因

这是插件显式实现的边界，不是模型不认识路径，也不是 IntelliJ Diff 必须限制在 Sources Root。

- `JavaEditTargetPort` 的选区、精确路径、项目枚举及 canonical 复查均调用 `ProjectFileIndex.isInSourceContent`。文件存在也可能返回 `OUTSIDE_SOURCE_ROOT`；选区/枚举路径会排除该文件，可能返回 `NOT_FOUND`。
- `JavaEditApplyPort.target()` 在应用前再次调用同一判断；只改定位器仍不能应用。
- 该 API 判断的是 IDEA 项目模型里的 source content（可包括测试源码），并非目录名称恰好叫 `src`。未正确导入模块或未标记源码根的 `src/demo/Calculator.java` 不满足条件；Maven 标准路径也需要 IDEA 正确导入。

## 来源与要求的区分

G2/G3 实现提交 `52fd92f` 引入该定位边界；`G23_IMPLEMENTATION.md` 明确记录“仅查项目目录内的 Java source roots”。F2 已确认 R01–R10、G0 D1 和契约提案表达原项目/现有单 Java 文件/源代码范围，当前未找到用户明确要求“必须位于 IDEA 标记的 Sources Root”的原文。不能将实现中的收窄反写成已确认用户要求。

代理推断：这是实施时选择的保守扫描/修改边界，便于排除非源码目录。它与真正必要的原项目归属、canonical 路径、VFS 身份、Java 类型、快照一致性、明确审核和写入前漂移检查不同；扩大到原项目内未标记的 Java 文件需要分别调整定位、方法解析与应用，并补充边界测试。

本轮保留该策略，只解释来源。用户如另行要求扩大范围，应先记录新范围，再同步两道检查与测试，不能仅删除单个判断。
