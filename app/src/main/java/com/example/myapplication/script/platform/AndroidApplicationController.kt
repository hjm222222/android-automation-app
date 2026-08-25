package com.example.myapplication.script.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

class AndroidApplicationController(
    private val context: Context
) : ApplicationController {
    private val packageManager = context.packageManager

    override suspend fun launch(packageName: String): ApplicationControlResult {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: return ApplicationControlResult.Failed("找不到应用启动入口：$packageName")

        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ApplicationControlResult.Success
        } catch (_: ActivityNotFoundException) {
            ApplicationControlResult.Failed("应用无法启动：$packageName")
        } catch (_: SecurityException) {
            ApplicationControlResult.Failed("系统拒绝启动应用：$packageName")
        }
    }

    override fun queryLaunchableApplications(): List<LaunchableApplication> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .map { resolveInfo ->
                LaunchableApplication(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString()
                )
            }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }
}
