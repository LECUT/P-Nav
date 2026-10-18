#!/bin/bash
# SPP 诊断日志采集脚本
# 用法：bash spp_diag.sh
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb"
OUT="spp_diag_$(date +%m%d_%H%M).txt"
{
  echo "=== SPP_DIAG (核心诊断) ==="
  "$ADB" logcat -d -s SPP_DIAG:* 2>/dev/null
  echo ""
  echo "=== DIAG_SPP (逐历元) ==="
  "$ADB" logcat -d -s DIAG_SPP:* 2>/dev/null
  echo ""
  echo "=== isSpp ==="
  "$ADB" logcat -d -s isSpp:* 2>/dev/null
  echo ""
  echo "=== SPP tag ==="
  "$ADB" logcat -d -s SPP:* 2>/dev/null
  echo ""
  echo "=== 采集时间: $(date) ==="
} > "$OUT"
echo "日志已保存到: $OUT ($(wc -c < "$OUT") bytes)"
