# 持久化职责核查

核查日期：2026-10-16。仅采用官方资料；这是技术研究记录，不是用户已批准的存储方案。

## 官方事实

- `PersistentStateComponent` 适用于应用或项目服务的可序列化状态，状态写入随 IDE 保存设置发生；不能据内存更新推定单条业务消息已落盘。见 [Persisting State of Components](https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html)。
- IDE configuration 与 system/cache 目录的用途不同，旧版本缓存可能清理。见 [Directories used by the IDE](https://www.jetbrains.com/help/idea/directories-used-by-the-ide-to-store-settings-caches-plugins-and-logs.html)。
- `PasswordSafe` 用于密码、API key 等敏感值，读写需放后台；普通设置可保存非秘密配置及凭据标识。Windows 默认后端为 KeePass 格式文件，用户可改变存储方式，不能保证总是 Windows Credential Manager。见 [Persisting Sensitive Data](https://plugins.jetbrains.com/docs/intellij/persisting-sensitive-data.html)。
- 应用级服务与项目级服务具有不同实例作用域；资源释放与数据删除不是同一动作。见 [Services](https://plugins.jetbrains.com/docs/intellij/plugin-services.html)、[Disposal](https://plugins.jetbrains.com/docs/intellij/disposers.html)。

## 本项目推论

平台没有为聊天规定某个必须换数据库的容量阈值。将增长的完整消息、代码快照、PDF 与 checkpoint 交给业务存储，将配置和少量视图状态交给设置职责，是本项目为了明确保存语义而提出的拆分。

已确认需要恢复的唯一 PDF 副本、聊天及代码快照不应仅依赖可清理缓存。实际目录、存储格式和向量库尚未选择；服务作用域也不等于必须分成多个物理数据库。

用户新增确认见 user-turn-016.md：托管 PDF 引用对应导入版本；恢复全部草稿与标签布局。该答复未选择具体技术产品。
