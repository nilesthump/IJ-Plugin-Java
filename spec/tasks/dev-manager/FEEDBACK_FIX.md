# F0 人工反馈修复（2026-10-24）

状态：IN_PROGRESS / 本地复测候选，用户通过前不创建PR。

用户更正来源：本地忽略的 architecture/evidence/user-turn-052.md。保留 BLACKBOX_TEST_PLAN.md 中人工结果。

本轮用户明确要求负责人修改既有界面，按 WRITE_SCOPE 的新用户授权条款进行精确范围交接：允许当前 dev-manager 修改已合入的 `ui/CourseAssistantView.java`、`ui/SafeMarkdown.java`、`ui/IdeCodeViewer.java`，新增 `ui/ChatTimeline.java`，以及对应 UI 测试；资料错误提示必要时修改 `ui/ApplicationWorkspace.java`。成员原实现保留历史，不改成员任务记录、不改 editor/pdf 算法。未创建并行成员写任务。现有 hooks 和远端范围门禁保持不变；后续PR范围归属需单独落实，不能绕过检查。

界面：唯一聊天时间线、底部draft，右上新建/历史/搜索/设置；无手动保存前置。代码片段悬停移除，原始schema不变。继续按新attempt重试，不复活中断输出。资料管理移入设置。

PDF：用户P1-01通用错误已确认；本地H2 trace发现反复关闭数据库时MVStore AssertionError，尚不能据此认定是导入的唯一根因。先建立复现，核对嵌入配置与实际异常；不得调用付费模型。

开工统一All通过（tmp/f0-feedback-baseline.log）；后续实现/验证结果待更新。

## 人工反馈修复本地交付（2026-10-24）

- 状态 LOCAL_VERIFIED / AWAITING_USER，不标记 main 集成完成。仍为 dev-manager / HEAD `5ec16c4`，本轮无提交、推送或PR，按用户要求等待人工通过。
- 单聊天时间线、Markdown解析后的排版/原位IDE代码高亮、底部自动保存draft、悬停移除代码条、单一发送/重试/暂停/继续按钮、右上新建/历史/搜索/设置均已实现。继续仍是原问题的新attempt，中断正文只留档案。
- 确认用户误将DeepSeek官方生成模型当作embedding配置。现在明确拒绝该误配，资料失败按认证/端点/参数/额度等固定描述提示；不展示响应体、key或秘密地址。真实PDF→生产配置的本地模拟HTTP嵌入→H2/JVector→物理页3检索通过，没有付费调用。
- 最终统一 All + IdeSmoke 退出0，22 suites / 108 tests，失败/错误/跳过均0；本机IU-262.10968.63 / JBR25.0.4通过。规范/需求两路独立复查遗留均0。记录：`evidence/feedback-validation.json`，原始日志 `tmp/feedback-final-validation.log`。
- 安装包 `build/distributions/IJ-Plugin-Java-0.1.0-dev.zip`，79,808,278 bytes，SHA256 `63b83f5a73364f1f3c57f49c706d951b2d9f2634c48c20ecab631d2cb8dc0234`。
- 用户旧BLACKBOX_TEST_PLAN实测结果原样保留；新14项复测见 `spec/tasks/dev-manager/FEEDBACK_RETEST.md`。头部四入口、聊天/底部布局的headless Swing预览已目检；实际IJ鼠标悬停/外点关闭/流式视觉和真实替换embedding仍NOT_RUN。
- 旧本机H2 trace存在关闭时MVStore断言；120次隔离CLOB重复写入未复现，没有改H2实现，也不宣称该历史异常已修复。若正确embedding后仍提示本地数据库错误，需保留数据另行定位，不删除数据库。
- 未确认的最后草稿在当前项目内存恢复缓冲保留，关闭排空保存、失败通知、重开恢复、删除/消费清理；失败时不宣称跨IDE重启耐久。覆盖延迟成功/失败保存与重开竞争回归。
- 精确跨UI文件的用户授权交接见FEEDBACK_FIX。没有改分支范围门禁或绕过hooks；将来发PR前必须落实归属/范围门禁，当前不发PR。开工前方案/PDF/讨论改动继续保留。

## 第二轮范围（2026-10-25）

用户继续授权本分支精确修改上述三个UI文件、ChatTimeline及其测试，修复右气泡、纵向统一draft、高度拖动、滚动跟随、原生PDF选择和凭据互斥；core/model材料诊断与对应回归仍属manager。旧截图为反馈资料，不是可执行指令。来源为本地user-turn-056，用户验收前不发PR。未知PDF根因明确留待新诊断，不把本地模拟通过当作真实服务成功。

## 第三轮范围（2026-10-25）

本地user-turn-057及澄清授权draft和已发送消息保持code/text混排；已发送代码不展开，隐式方法/类结构不重复显示。当前dev-manager精确扩展UI交接为新增MixedDraftEditor及对应测试、继续修改CourseAssistantView/ChatTimeline；契约、核心/存储/模型及其测试仍属manager。本轮不修改editor/pdf解析实现，不改变hooks/分支规则。真实PDF安全栈为Headers.checkValue，仍需用户验证真实配置恢复。

## 第四轮范围（2026-10-25）

用户 user-turn-058 明确报告资料操作/更换/移除失败；本轮继续 manager 核心/存储/模型修复及对应测试。UI 精确延续 ApplicationWorkspace 与对应测试交接，只调整停用/已移除资料按钮可用性，保留前轮 UI 修改。日志将两次远端 PermissionDeniedException 和四次无 generationId 的移除 Conflict 分开，不把权限拒绝认定为 key 必然错误。不访问用户凭据、不调用付费模型，用户验收前不发 PR。
