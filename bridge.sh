#!/system/bin/sh
# Bridge最小版：通过Shizuku权限执行命令并写日志
LOG=/sdcard/bridge_log.txt
echo "==== Bridge启动：$(date) ====" >> $LOG

# 示例：获取系统版本
getprop ro.build.version.release >> $LOG 2>&1

# 示例：扫系统命令目录
ls /system/bin >> $LOG 2>&1

echo "==== Bridge执行完毕 ====" >> $LOG
