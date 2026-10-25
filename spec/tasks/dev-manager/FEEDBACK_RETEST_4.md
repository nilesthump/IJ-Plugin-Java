# PDF 导入、失败资料移除与重试复测（第四轮）

日期：2026-10-25。人工结果均为 NOT_RUN；前轮人工记录保留。用户通过前不发 PR。

## 准备

1. 在 IDEA 保存工作，停用 Course Assistant；退出 IDEA。不要再次清空 H2，保留失败资料用于复测。
2. 安装 `build/distributions/IJ-Plugin-Java-0.1.0-dev-feedback4.zip`（设置 → 插件 → 齿轮 → 从磁盘安装），按 IDEA 提示重启。
3. 打开 Course Assistant → 右上角设置 → 服务配置。生成服务保留有效配置；嵌入供应商使用用户提供的 `tongji-newapi`，Base URL 为 `https://agi.tongji.edu.cn/v1`，模型为平台展示的准确标识 `Qwen3-Embedding`。维度按该平台此模型实际支持值填写，本次未验证真实维度。
4. 在平台个人控制台核对嵌入令牌是否启用、模型限制是否允许该模型、令牌分组/模型渠道权限及 IP 白名单。HTTP 403 也可能来自服务端网关策略，不能只凭状态码判断 key 错误。可核对平台失败日志，不要将 key 或完整请求头复制到反馈中。
5. 如修改 key，在插件停用时填写对应的“嵌入 API key”，不要勾选移除凭据，保存后启用。已保存状态不代表远端认证通过。生成 key 和嵌入 key 分开保存。
6. 准备一份可提取文本的真实 PDF，以及仓库 `spec/tasks/dev-manager/blackbox-fixtures/course-a-v1.pdf`。使用“导入 PDF”或“更换所选”打开文件选择窗口。

## 逐项测试

| 编号 | 操作 | 预期 | 人工结果 |
| --- | --- | --- | --- |
| PDF4-01 | 在首次导入失败、没有就绪课程库时选中 FAILED 资料，点击移除所选 | 成功标记 REMOVED，无 Conflict，无嵌入网络请求；其他资料不受影响 | NOT_RUN |
| PDF4-02 | 关闭并重开 IDEA，查看刚移除的资料 | 仍为 REMOVED，不能再次点击更换或移除该条；不会自动继续网络任务 | NOT_RUN |
| PDF4-03 | 权限尚未解决时选中另一失败资料，点击更换所选，选择 PDF | 若服务仍拒绝则明确显示 HTTP 403 及权限检查提示；不宣称发布成功，不自动重试 | NOT_RUN |
| PDF4-04 | 解决平台权限后，选中 FAILED 资料，更换所选，选择同一或另一 PDF | 同一资料身份变成 READY；可检索，页码对应文件；失败历史不重复显示成新就绪资料 | NOT_RUN |
| PDF4-05 | 对已移除资料的同一 PDF 使用“导入 PDF” | 显式新导入可以完成；保留旧 REMOVED 历史身份，仅一个新 READY 条目，不被旧 MD5 记录堵住 | NOT_RUN |
| PDF4-06 | 同时存在 READY 和 FAILED 资料时移除 FAILED | 原 READY 资料和检索仍可用 | NOT_RUN |
| PDF4-07 | 资料处理过程中移除该未就绪资料，等待原任务结束 | 已移除资料不会被迟到结果重新激活；可能提示原任务状态过期 | NOT_RUN |
| PDF4-08 | 插件停用时或选中 REMOVED 行 | 更换/移除按钮不可点击；启用并选中有效资料后恢复 | NOT_RUN |

真实服务调用由用户手动进行。自动测试使用本地 HTTP 模拟 403/成功响应、真实 PDFBox、Spring AI 客户端与 H2/JVector；不证明同济网关模型权限或真实服务恢复。若仍失败，请记录操作名、时间、任务编号与 HTTP 状态；平台日志若给出具体原因，只反馈去除凭据后的原因。

New API 令牌配置说明：https://docs.newapi.pro/zh/docs/guide/feature-guide/user/token 。该说明描述通用 New API，不能证明同济部署的具体配置。
