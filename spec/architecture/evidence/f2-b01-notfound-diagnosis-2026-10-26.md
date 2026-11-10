# B01 后续现场诊断（2026-10-26）

## 可取得用户原文

ERROR · 操作失败，请查看任务状态后重试

发送 B01 后生成过程中

我删除了，稍等我再弄一次

无法生成代码修改候选
我尝试定位目标方法，但解析未成功：

最后两句伴随两张截图；截图显示NOT_FOUND。此句是代理转述，非补造截图全文。

## 现场证据及结论（代理）

隔离只读数据库副本位于tmp/f2-notfound-1fcbb228e13e4174a4a0bb4e75eddad3，不修改原库、没有模型调用。轮次c18decd8-b3d2-4678-adfe-ee960eda0895绑定项目H:/programming-exam，实际工具参数path=src/demo/Calculator.java，class=demo.Calculator，method=add，parameters=[int,int]，返回NOT_FOUND。该轮SUCCEEDED表示正常解释工具未找到，不代表生成候选。

已通过文件系统核验：H:/programming-exam/src/demo/Calculator.java不存在；实际文件为H:/programming-exam/part1-library/src/demo/Calculator.java，package demo，add(int,int)确为return a-b。本次路径缺part1-library前缀，NOT_FOUND符合精确路径规则；不能自动fallback修改其他同名文件。

保存的.idea/programming-exam.iml没有sourceFolder声明，part1-library/pom.xml未定制sourceDirectory；文件位于src/demo而非Maven默认src/main/java。运行时是否已临时配置Sources Root未读取，须在当前工程确认part1-library/src为Sources Root，否则完整路径仍可能OUTSIDE_SOURCE_ROOT。未改外部工程任何文件。

此前通用ERROR：本地trace存在同时段H2关闭压缩FreeSpaceBitSet断言，但当时失败聊天已被用户删除；仅可作为关联线索，不能证明它是当前NOT_FOUND的原因。合成200次写入/关闭和副本只读/三次关闭未复现该断言，未修改H2、未禁用断言、未清库。本次读到旧失败轮次的failureType=ProtocolFailure，未保存异常message，不能进一步归因于H2。

截图建议粘贴源码属于模型回复，不当作agent操作命令。当前无产品代码变更，不PR/推送/合并；人工B01仍FAIL/待复测。