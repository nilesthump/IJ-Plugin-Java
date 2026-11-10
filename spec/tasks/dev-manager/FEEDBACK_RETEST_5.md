# TLS 失败定位与真实 API 继续验证

日期：2026-10-25。用户已明确授权“你直接使用真实api”，后续不重复索要 API 调用许可。不得在聊天、日志或普通文件里保存 key。

## 已有证据

- 任务 cd38a9a6 的安全栈为 SSLHandshakeException → EOFException，发生于嵌入客户端 HTTPS 握手，PDF 已进入嵌入步骤；不能据此推定 key 是否有效。
- 系统无凭据 HEAD 首页返回200，同版 JBR 直接握手成功 TLSv1.3；仅证明检查时的连通性，不证明真实 embedding 请求成功，也不能确定先前中断发生在远端还是网络路径。
- 本地真实 PDF + 同 Spring AI 客户端 + 本机 TLS 对端中途关闭连接，复现握手失败；新提示不再误称本地处理失败，保留资料未发布和原始诊断。

## 继续真实调用前的工具恢复

computer-use 的 node_repl 初始化、reset后重试和备用 cua_repl 均报 `failed to write kernel assets: 系统找不到指定的路径。(os error 3)`，没有新的真实 API 请求。工作区和系统临时目录存在，不能把工具故障当成模型故障。

重启 Codex 后继续，IDEA 可保持打开。优先直接操作现有插件与已保存凭据，无需先安装第五轮包或清空 H2。第五轮新包仅改善 TLS 分类，不更换客户端/证书/网络策略，不能承诺解决外部连接中断。

## 真实验证步骤（已授权，尚未执行）

1. 确认当前嵌入非秘密配置仍是用户指定的 tongji-newapi / https://agi.tongji.edu.cn/v1 / Qwen3-Embedding，维度保持用户配置；不打开或读取 key。
2. 使用小型合成 PDF（仓库 blackbox-fixtures/course-a-v1.pdf），通过插件导入。若同文件已有 FAILED 条目，选择该测试条目执行更换；不动用户其他资料。
3. 观察新任务完成/失败状态，按任务编号检查安全日志。只作一次显式尝试；若因具体错误需要另一次验证，明确说明目的，不无限自动重试。
4. 成功需记录实际 READY、物理页数与任务时间；只有握手或首页HTTP成功不能算API成功。失败分别记录HTTP状态/TLS异常等事实，不公开响应中的凭据或私人内容。
5. 本轮不自动创建PR；测试条目如需移除仅针对本轮测试身份，保留用户既有资料。

## 安装新诊断包（可选）

保存工作，停用插件，在 IDEA 设置 → 插件 → 从磁盘安装中选择 build/distributions/IJ-Plugin-Java-0.1.0-dev-feedback5.zip，按提示重启。无需清空资料库或重新填写凭据。

| 项目 | 预期 | 真实人工结果 |
| --- | --- | --- |
| TLS 握手中断 | 明确提示 TLS、安全连接及网络/代理/证书检查；不宣称 key 错误 | NOT_RUN |
| 本地 PDF EOF | 仍按本地错误诊断，不误标为 TLS | NOT_RUN |
| 使用真实嵌入 API 导入 | READY 或保留准确服务错误；实际请求结果待工具恢复后确认 | AUTHORIZED / BLOCKED_BY_TOOL_STARTUP |
