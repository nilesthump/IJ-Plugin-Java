# F2-G6：全链路验证、人工验收与交付

负责人：用户本人。依赖：G5通过。状态：LOCAL_VERIFIED / AWAITING_USER。人工通过后再统一PR合并；本地成果见[G6_IMPLEMENTATION](G6_IMPLEMENTATION.md)和[BLACKBOX_TEST_PLAN](BLACKBOX_TEST_PLAN.md)。

## 工作项

1. 按VALIDATION_MATRIX完成两种入口→候选→原生Diff→同意/拒绝→结果/Undo→再次draft的全链路。模型与嵌入使用合成/本地受控响应证明本地逻辑，不调用付费模型。
2. 全量回归已验收F0：问答/引用及课程故障隔离、资料硬删除、混排/IME/顺序、滚动/恢复、项目/session隔离、停止/未完成历史、摘要指纹及删除全清。
3. 更新用户黑盒操作文档：插件安装/配置复用、合成Java工程准备、文字/选区两入口、重名/重载、未保存文本、Diff只读、两个按钮、重复点击、漂移、Undo/保存、关闭/重启。
4. 独立Standards/Spec检查，修复并复查；统一All、实际本机-IdeSmoke、插件ZIP检查，保留失败日志/修复/最终结果，不把Repository通过当产品验收。
5. 当前既有dev-manager按已生效G0规则直接PR main，最新head须validation/scope通过，再合入、fetch核验并保留固定分支。没有用户发布/合并授权时先完成可审阅内容，不伪造远端完成。
6. 逐组STATUS/HANDOFF、方案/API与公共CURRENT回填真实状态。文档生成、LOCAL_VERIFIED、main开发交付DONE、用户人工验收、真实模型质量分别记录。

## 交付物

完整源码与测试、准确契约/开发方案、F2人工黑盒步骤、统一验证/真实IDE证据、独立检查和PR/main核验。最终作业英文报告/演示若另有整体任务应同步实际F2行为，不据任务定义声称已制作。

## 退出条件

矩阵所有必需本地场景有实际证据且无未闭合问题；main开发交付满足门禁，用户实际验收另记；未执行的真实API、规模与模型质量不写PASS。F2不得破坏F0验收基线，也不因本组完成宣称架构正式冻结。
