#!/system/bin/sh
# NEMESIS postinstall (root, cwd = package dir). NEMESIS has already installed
# app/Preconditioning.apk as a normal Android app ([app.0] kind=apk, pm install), and
# svcmgr starts the knobd service after this returns. Puts back the settings the
# previous version's preuninstall (update) or preinstall (1.x migration) saved,
# then starts the app's service once. Never fails the install: the app works
# without old settings, and a backup that could not be restored is kept.
. /data/nemesis/apps/kr.neev.preconditioning/scripts/common.sh

rm -rf "$WORK"    # leftovers from 1.x's /system staging

if ! android_up; then
    # NEMESIS installs the APK on first launch when Android is not running yet.
    log "postinstall: Android is not running; settings restore and star integration skipped; reinstall after boot"
    exit 0
fi

APK=$(app_path)
case "$APK" in
    /data/*) log "postinstall: Android has $APP_PACKAGE installed at $APK" ;;
    *) log "postinstall: WARN: pm path shows '${APK:-nothing}' for $APP_PACKAGE, expected /data/app" ;;
esac

if prefs_backed_up; then
    restore_prefs || log "postinstall: WARN: settings not restored; the backup stays in $PREFS_BACKUP"
fi

# Last step: a force-stop after this would undo it (see SERVICE_COMPONENT).
load_boot_env
OUT=$(am startservice -n "$SERVICE_COMPONENT" 2>&1)
case "$OUT" in
    *Error*) log "postinstall: WARN: could not start $SERVICE_COMPONENT: $OUT" ;;
    *) log "postinstall: started $SERVICE_COMPONENT" ;;
esac
if [ "$(getprop ro.build.product)" = NEEV.KOR.0000.V039.001.260902 ]; then
    if sh "$HERE/star/install.sh"; then
        log "postinstall: star integration ready; restart head unit"
    else
        log "postinstall: star integration unavailable; app installed, see install output"
    fi
fi
exit 0
