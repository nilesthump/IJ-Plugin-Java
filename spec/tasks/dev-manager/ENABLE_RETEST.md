# 启用失败复测

本轮仅改进并验证启用错误处理，尚未取得用户实际失败的原始异常，不能宣称已确认根因。无提交/推送/PR。

1. 在 IJ 用 Install Plugin from Disk 安装 `build/distributions/IJ-Plugin-Java-0.1.0-dev-enable-diagnostics.zip`，重启后打开 Course Assistant。
2. 点右上设置，查看“生成凭据 / 嵌入凭据”的“已保存 / 未保存”。不显示key；已保存仅表示本地凭据存在，不代表平台认证成功。
3. 点击启用。缺少哪类key、缺维度、地址无效会明确说明；其他错误显示类型及“诊断”编号。若失败，请复制整条新提示及两类凭据状态，不要提供key。先不重复导入PDF。
4. 若提示key未保存，在OFF状态进入服务配置，填写对应槽并确认保存，再启用；若两类均已保存却失败，保留提示和时间，检查idea.log中的 `F0 command diagnostic <编号>`。

诊断日志仅记录异常类型与调用栈，不输出异常message/响应体/配置/key。原异常在本进程中按命令ID保留；重启会丢失该内存记录，因此持久日志保留安全栈。

已验证：同样非秘密模型标识（deepseek-flash / Qwen3-Embedding、1024）配合合成key，在JUnit及真实本机IDE内仅构造/关闭客户端成功，无外部API请求。缺凭据/缺维度提示先失败后修正；注入LinkageError的状态回退先失败后修正。实际用户能否启用仍待复测。
