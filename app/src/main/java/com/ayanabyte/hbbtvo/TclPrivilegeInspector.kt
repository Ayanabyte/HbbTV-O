package com.ayanabyte.hbbtvo

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

/**
 * Read-only privilege audit. Does not attempt to impersonate TCL's platform
 * signature or UID 1000. Any elevated development permission must be granted
 * explicitly by the owner through ADB.
 */
class TclPrivilegeInspector(private val context: Context) {
    fun inspect(): List<String> {
        val report = mutableListOf<String>()
        val pm = context.packageManager
        val packageName = context.packageName
        report += "== Permisos TCL: auditoría =="

        fun uidOf(name: String): String = runCatching {
            pm.getApplicationInfo(name, 0).uid.toString()
        }.getOrElse { "no accesible (" + it.javaClass.simpleName + ")" }

        report += "UID HbbTV O!=" + Process.myUid()
        report += "UID TCL TV=" + uidOf("com.tcl.tv")
        report += "UID TCL TIF=" + uidOf("com.tcl.tvinput")

        for (other in listOf("android", "com.tcl.tv", "com.tcl.tvinput")) {
            val comparison = runCatching { pm.checkSignatures(packageName, other) }
                .getOrElse { PackageManager.SIGNATURE_UNKNOWN_PACKAGE }
            val label = when (comparison) {
                PackageManager.SIGNATURE_MATCH -> "COINCIDE"
                PackageManager.SIGNATURE_NO_MATCH -> "DISTINTA"
                else -> "no comparable (" + comparison + ")"
            }
            report += "Firma vs " + other + ": " + label
        }

        val declared = runCatching {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.toSet() ?: emptySet()
        }.getOrDefault(emptySet())

        val list = listOf(
            "android.permission.READ_TV_LISTINGS",
            "com.android.providers.tv.permission.READ_EPG_DATA",
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.TV_INPUT_HARDWARE",
            "android.permission.INJECT_EVENTS",
            "com.mstar.android.permissionn.ACCESS_TV_DATA"
        )
        for (permission in list) {
            val isGranted = runCatching {
                pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            val shortName = permission.substringAfterLast('.')
            report += "Permiso " + shortName + ": declarado=" + (permission in declared) + "; concedido=" + isGranted
        }

        val secure = pm.checkPermission(
            "android.permission.WRITE_SECURE_SETTINGS", packageName
        ) == PackageManager.PERMISSION_GRANTED
        report += if (secure) {
            "ADB desarrollo: WRITE_SECURE_SETTINGS concedido. No implica UID de sistema."
        } else {
            "ADB desarrollo: WRITE_SECURE_SETTINGS no concedido."
        }
        report += "== Fin auditoría permisos =="
        return report
    }
}
