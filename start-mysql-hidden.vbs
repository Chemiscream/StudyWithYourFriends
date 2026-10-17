' 开机静默启动便携 MySQL（学习记录服务依赖）
Set sh = CreateObject("WScript.Shell")
sh.Run """C:\Files\dev\mysql-8.0.28-winx64\bin\mysqld.exe"" --defaults-file=""C:\Files\dev\mysql-data\my.ini""", 0, False
