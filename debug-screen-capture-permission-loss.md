# Debug Session: screen-capture-permission-loss
- **Status**: [OPEN]
- **Issue**: 点击"制作脚本"加号后，Activity 跳转到桌面并创建悬浮窗，但在此过程中录屏权限丢失（服务/界面表现为无法再使用录屏截图能力）。
- **Debug Server**: http://127.0.0.1:7777/event
- **Log File**: .dbg/trae-debug-log-screen-capture-permission-loss.ndjson

## Reproduction Steps
1. 打开应用，进入主界面。
2. 打开录屏权限（MediaProjection 授权成功，resultCode=RESULT_OK）。
3. 点击"制作脚本"加号（+）。
4. 观察：Activity 跳转到桌面，FloatingWorkspaceService 创建悬浮窗。
5. 在悬浮窗中执行需要录屏/截图的功能，发现录屏权限已丢失。

## Hypotheses & Verification
| ID | Hypothesis | Likelihood | Effort | Evidence |
|----|------------|------------|--------|----------|
| A | FloatingWorkspaceService.onStartCommand 收到的 Intent 中没有授权 resultCode/resultData（MainActivity 凭据未传入） | High | Low | Pending |
| B | MainActivity 重建后 savedInstanceState 为 null，主界面误判录屏状态为"未授权"（实际 MediaProjection 仍有效，因为服务持有凭据） | High | Low | Pending |
| C | finishAndRemoveTask 后应用进程被系统/厂商清理，服务 onDestroy，MediaProjection 授权随之失效 | Med | Med | Pending |
| D | 授权 data Intent 在 onSaveInstanceState 序列化/恢复过程中丢失，恢复后 data 为 null | Med | Low | Pending |
| E | getMediaProjection 实际调用失败（Android 14+ 媒体投影会话限制或 token 过期） | Low | High | Pending |

## Log Evidence
[待收集]

## Verification Conclusion
[待对比]