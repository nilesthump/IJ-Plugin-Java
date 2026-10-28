# 用户反馈原文（2026-10-25）

ERROR · 资料操作失败，新版本未发布：嵌入服务认证或权限失败，请在停用后检查嵌入 API key（任务 ddaef0c4-3937-4d2d-b6e5-846a46aafca6）
ERROR · 资料操作失败，新版本未发布：本地处理失败（Conflict），请提供诊断编号或任务编号，以检查 IDEA 日志（任务 c6692999-b8b6-487c-9d69-cc437fc5e158）
ERROR · 资料操作失败，新版本未发布：本地处理失败（Conflict），请提供诊断编号或任务编号，以检查 IDEA 日志（任务 536199c1-0433-4cda-93bd-15018a10ff17）
ERROR · 资料操作失败，新版本未发布：本地处理失败（Conflict），请提供诊断编号或任务编号，以检查 IDEA 日志（任务 4a5603b8-d4b7-4c91-93c8-15542ea1dfaa）
ERROR · 资料操作失败，新版本未发布：本地处理失败（Conflict），请提供诊断编号或任务编号，以检查 IDEA 日志（任务 41e5bc6e-28c7-49c2-abaa-8ece9d3e8425）
ERROR · 资料操作失败，新版本未发布：嵌入服务认证或权限失败，请在停用后检查嵌入 API key（任务 b3882a3d-bbd8-4241-86ed-2f71ab445341）

1是操作失败，2是无法更换失败的pdf

## 用户澄清原文

tongji-newapi  [https://agi.tongji.edu.cn/v1](https://agi.tongji.edu.cn/v1)Qwen3-Embedding
更换所选和移除所选都失败

## 代理分析（非用户原文）

安全日志中两个认证失败均为 PermissionDeniedException，四个 Conflict 均来自 CourseLibrary.remove 无 generationId 分支。用户确认更换也失败，需测试同一路 HTTP 403 更换失败与本地失败条目移除两条路径。不得推定 key 本身错误或远端权限具体原因，不调用真实付费模型。继续用户验收前不 PR。
