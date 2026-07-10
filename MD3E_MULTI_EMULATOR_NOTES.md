# v0.1.47 Notes

- GBA 关闭作弊码时改为冷重启 `:internal_gba` 进程，重新创建 mGBA core。
- 解决穿墙码这类 ROM/指令 patch 在当前 core 内无法通过 `resetCheat()` 释放的问题。
- 关闭后尝试恢复开启作弊前的 clean state。
- 继续保留自定义作弊码 UI：添加、A 启用/关闭、X 删除、B 返回。
