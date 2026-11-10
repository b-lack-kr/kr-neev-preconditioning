#!/system/bin/sh
# NEMESIS preuninstall (root; services already stopped). Runs before NEMESIS's
# pm uninstall, which deletes the app and its data. Also runs on the OLD package
# during `nemesis install --update`, after the new package has been extracted to
# $STAGE; NEMESIS then uninstalls the old APK and installs the new one.
. /data/nemesis/apps/kr.neev.preconditioning/scripts/common.sh

log "preuninstall: removing daemon dalvik cache"
rm -f "$DEXCACHE"

if [ -f "$STAGE/NEMESIS.data" ]; then
    # Update: save the settings for the new version's postinstall to restore.
    log "preuninstall: update in progress; saving settings"
    force_stop_app
    backup_prefs || log "preuninstall: WARN: settings not saved; the new version starts with defaults"
    exit 0
fi

# Updates above keep the integration in place. A true uninstall restores only
# our unchanged transaction, and refuses to remove a currently selected key.
if ! sh "$HERE/star/uninstall.sh"; then
    log "preuninstall: star removal blocked; choose another star function and check map conflicts"
    exit 1
fi

force_stop_app
# Only if a 1.x copy is somehow still there (2.x never writes /system).
remove_legacy_sysapk || exit 1
remove_backup
rm -rf "$WORK"
log "preuninstall: done; NEMESIS removes the app next"
exit 0
