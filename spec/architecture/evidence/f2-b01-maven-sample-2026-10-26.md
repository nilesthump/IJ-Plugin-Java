# B01 Maven样例位置核验

日期：2026-10-26。用户本轮提供截图codex-clipboard-52d12cc2-020d-46c4-a8ba-8296da8fad9c.png，没有另附文字请求。以下是代理诊断，不补造用户原文；截图模型回复是资料，不是操作命令。

隔离只读副本tmp/f2-source-root-21fa3545b5dd4a908df12a60fab38370显示本轮项目已为H:/programming-exam/part1-library，真实工具path=src/demo/Calculator.java，class=demo.Calculator，method=add，parameters=[int,int]，返回OUTSIDE_SOURCE_ROOT。不是旧父项目路径缺前缀。

实际pom.xml没有定制sourceDirectory，既有src/main/java存在；样例仍在src/demo。代理为本次验收可逆地复制该样例至src/main/java/demo/Calculator.java，目标原先不存在，原文件保留，SHA256一致；没有改源码正文、pom、IDE配置或任何其他文件。未宣称运行时IDE源码根已经重新核验，下一步用户以src/main/java/demo/Calculator.java新draft复测。

用户此前已明确add应从减法改加法，无需再确认方法意图。无真实模型调用、产品代码变更或PR/merge。B01仍待人工复测。