# F0 架构方案阅读版

主文件：[F0_DEVELOPMENT_PLAN.pdf](F0_DEVELOPMENT_PLAN.pdf)。这是开发基线的 PDF 阅读产物，不是正式候选、冻结架构、英文结课报告或产品验收。

- 正文单源：[综合开发方案](../../spec/architecture/F0_DEVELOPMENT_PLAN.md)，11 节；当前初始化状态见 [CURRENT](../../spec/progress/CURRENT.md)。
- 两张图的可编辑源与实际渲染 PNG：[组件图](../../spec/architecture/diagrams/f0-components.puml)、[ReAct 图](../../spec/architecture/diagrams/f0-react.puml)。
- `F0_DEVELOPMENT_PLAN.model.json` 保存本次正文块/表格与稳定章节 ID；它是渲染模型，不是技能的正式 candidate。
- `F0_DEVELOPMENT_PLAN.manifest.json` 绑定源文、模型、PDF、图源和图片 hash；`F0_DEVELOPMENT_PLAN.verification.json` 记录独立文本提取、书签、页内边界与人工视觉检查。
- 导出只使用公开方案，不包含原始作业 PDF、原始图片、参考 TXT、需求答复或 WORKING.json。

## 重新生成

作者工具与插件运行依赖分开：Python 需 ReportLab、Pillow、pypdf、pdfplumber；Java 运行本地 PlantUML JAR，Windows 字体目录提供 Deng.ttf / Dengb.ttf。工具版本及 JAR/字体 hash 见 manifest。首次导出使用 PlantUML 官方发布的 1.2026.9；JAR 仅在忽略目录 `tmp/pdfs/tools`，不纳入插件或仓库依赖。

在仓库根目录执行，按本机路径指定 Java 和 JAR：

```powershell
python scripts/architecture/export_pdf.py --plantuml-jar tmp/pdfs/tools/plantuml-1.2026.9.jar --java H:/jdk-25.0.2/bin/java.exe
pdftoppm -scale-to 1400 -png output/pdf/F0_DEVELOPMENT_PLAN.pdf tmp/pdfs/page
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope Repository
```

导出器对图表渲染、全文片段、书签和边界失败直接报错；每次重跑会将人工视觉检查重置为 PENDING。检查最新全部页面、表格、两图与中文后，再在 verification 记录所检查的 PDF hash 和结果；不能复用旧版人工结论。文本提取不代替视觉检查，仓库结构检查也不证明 IDE/API/业务运行。
