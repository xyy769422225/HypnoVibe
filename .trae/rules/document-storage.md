# 文档存放规范

## 持久化文档
- 所有需要长期保留、版本管理的项目文档统一存放在 `document/` 目录下
- 例如：设计文档、架构文档、API 文档等

## 临时文件规范
- 所有 AI 生成的临时文件、脚本、中间产物等统一存放在 `ai_temp_data/` 目录下，按类型分目录存放：
  - `ai_temp_data/doc/` — 临时文档、草稿
  - `ai_temp_data/script/` — 临时脚本
  - `ai_temp_data/data/` — 临时数据文件
  - `ai_temp_data/other/` — 其他临时文件
- 该目录不纳入版本管理，可随时清理
