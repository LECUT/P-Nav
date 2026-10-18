#!/bin/bash
# SPP 诊断日志采集 — 你回来后运行: bash gather_spp_logs.sh > spp_diag.txt
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb"
echo "=== SPP_DIAG 日志 ==="
"$ADB" logcat -d -s SPP_DIAG:* 2>/dev/null
echo ""
echo "=== DIAG_SPP 日志 ==="
"$ADB" logcat -d -s DIAG_SPP:* 2>/dev/null
echo ""
echo "=== isSpp 日志 ==="
"$ADB" logcat -d -s isSpp:* 2>/dev/null
echo ""
echo "=== 采集时间 ==="
date
