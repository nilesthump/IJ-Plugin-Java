# 平台示例对齐与403分类复测

2026-10-25。用户已确认key启用且套餐包含；不再重复要求检查这两项。用户已授权真实API，但代理浏览器/桌面工具仍启动失败，本轮远端验证尚未执行。

## 安装准备

1. 保存IDEA工作，停用Course Assistant，从磁盘安装 build/distributions/IJ-Plugin-Java-0.1.0-dev-feedback6.zip，按提示重启。
2. 保留现有凭据与H2，不用清库或重新填key。嵌入配置应为平台截图的Base URL https://agi.tongji.edu.cn/v1、模型Qwen3-Embedding。
3. 本轮显式发送encoding_format=float，与平台示例一致。原SDK默认base64已由本地HTTP抓取证实；不据此认定它就是403根因。dimensions沿用现有用户配置，截图未确认真实维度，不任意改值。

## 只做一次真实导入/更换验证

启用插件，选中现有小型合成PDF的FAILED测试条目，点击更换所选并选回同一文件；若没有该测试条目，用导入PDF选择 spec/tasks/dev-manager/blackbox-fixtures/course-a-v1.pdf。不更换其他课程资料，不自动重试。

| 结果 | 含义与后续 | 人工结果 |
| --- | --- | --- |
| READY | 本次真实解析/嵌入/发布成功，记录时间；不能推定旧403原因已确定 | NOT_RUN |
| HTTP403 + HTML | 收到HTML错误页，可能来自网关/访问控制层，不能据此确认模型权限问题；记录任务编号 | NOT_RUN |
| HTTP403 + 模型/IP/令牌/额度类别 | 服务明确错误码或特定短语被翻译为固定类别；核对对应平台策略，不展示原始响应 | NOT_RUN |
| HTTP403 + JSON未识别或响应未识别 | 仍没有可确定的具体原因，记录任务编号，需要平台侧关联该次请求；不笼统声称key错误 | NOT_RUN |
| TLS握手失败 | 按连接失败处理，不能据此判断key；无需清库 | NOT_RUN |
| 维度不匹配 | 按提示核对配置与实际响应维度，旧资料不覆盖 | NOT_RUN |

反馈只需完整新提示和任务编号，勿附key或原始请求头。

## 白盒验证边界

本地真实SDK请求同时验证/v1/embeddings、独立嵌入凭据槽、原样模型名、encoding_format=float；本机HTTP模拟HTML/JSON403覆盖真实SDK错误适配。日志只增加自有固定类别，不复制任意服务正文、错误码或地址。以上不等于同济平台真实请求成功。
