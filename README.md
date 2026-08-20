# TianXing-Backend
天行平台后端demo

demo_backend中展示了一个基于spring boot + mysql + maven + mybatis开发后端的例子。

这次专业实习需要处理和展示的数据对象主要是各种天气事件的预报数据，通常来说，数据库中的这些数据具有这样的字段：

INT id;
String year;  # 模型预报的起报年份
String month; # 模型预报的起报月份
String var_model; # 预报的变量和模型名称
JSON data; # 预报结果

PS：预报结果通常来说是高维数组，第一个维度一般代表预报时间维度，如果预报的是气象指数，则data的格式为[time, index]；如果预报的是格点数据，data格式为[time,lat,lon]

demo中示例了一个Meteo实体对象的查询和简单数据加工过程（Meteo仅仅用作例子，并不在真正的开发内容中），具体包含：

· 查询全部
· 条件查询
· 查询到结果data部分的JSONString如何转换成Java高维数组

同时，为了方便代码阅读，请在开发过程中合理正确地使用注释、命名变量，避免带来过高的维护成本。

## 2.7–2.9 管理端交付文档

- 环境准备与演示：`docs/evaluation-admin-demo-and-delivery.md`
- 可直接执行的测试说明：`docs/evaluation-testing-guide.md`
- 管理 API：`docs/evaluation-admin-api.md`
- NSIDC SIC/SIE 科学口径：`docs/nsidc-scientific-evaluation.md`
- 已完成的测试结果：`docs/evaluation-backend-test-report.md`


