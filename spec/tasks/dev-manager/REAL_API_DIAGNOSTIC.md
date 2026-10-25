# 最小真实嵌入请求对照

用户已授权真实 API；当前代理浏览器/桌面工具无法启动，因此脚本需要用户在本机输入凭据。不要把 key 发到聊天里，也不要把 key 写在命令参数、普通文件或脚本里。

在 Windows PowerShell 执行：

```powershell
powershell -NoProfile -File "H:\IJ-Plugin-Java\scripts\diagnostics\Test-EmbeddingEndpoint.ps1"
```

出现隐藏输入提示后粘贴当前嵌入 API key，按回车。输入不显示字符；key只用于本次进程中的Authorization，不写配置文件。结束时释放SecureString/BSTR和请求对象；.NET字符串内存清除是尽力处理，不承诺无内存残留。

请求固定使用平台截图示例的 Base URL、Qwen3-Embedding、单句合成文本与encoding_format=float，不传dimensions。只有一次调用，无应用层重试、不跟随重定向，不发送PDF/聊天/代码。脚本仍使用系统默认TLS校验和.NET网络设置，不是插件/IDEA网络路径的完全等价验证。

请反馈输出JSON的HTTP、code/type/message等错误字段，或成功的VectorDimensions。输入key会被精确遮蔽，常见Bearer/sk/长token及控制字符也被处理；不要另附完整请求头或key。完整响应正文不会打印或落盘。

- HTTP200且返回一条float向量：仅表示该最小真实请求成功。可据实际维度继续对照插件的dimensions与SDK请求，不能直接判定插件RAG成功或旧403根因。
- HTTP403：保留实际错误码/原因用于定位服务端拒绝，不再凭状态码猜测key禁用/套餐缺失。
- HTTP3xx：停止，不把凭据转发到跳转地址。
- HTTP200但EmbeddingCount为0：不算有效嵌入成功。
- REQUEST_FAILED：只输出异常类型链，避免异常message泄漏凭据；继续按网络异常定位。

脚本是独立开发诊断，不注册为插件工具，不改变插件配置或H2，不需要安装新插件包。PowerShell5.1与7的本地模拟HTTP测试覆盖403脱敏、200维度、302不跟随及空200，共8项；真实调用结果尚待用户执行。
