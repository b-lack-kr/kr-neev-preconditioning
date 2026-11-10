#!/system/bin/sh
# Remove only this module's intact transaction. Never overwrite another mod's
# map changes and never alter the user's physical button assignments.
set -u
export PATH=/system/bin:/system/xbin:/data/nemesis/bin
unset LD_PRELOAD HMGAPI_SELFTEST HMGAPI_ROOT HMGAPI_HOST HMGAPI_PRODUCT HMGAPI_ONEBIN HMGAPI_PROPS HMGAPI_TEST_ANY_CAR HMGAPI_LPATCH HMGAPI_HMGAPID
WORK=/data/nemesis/neev-star-options
TXN=$WORK/transaction
KIT=$WORK/kit-0.1.0
[ -e "$WORK" ] || exit 0
[ -d "$WORK" ] && [ ! -L "$WORK" ] || exit 1
mkdir "$WORK/lock" || exit 1
trap 'rmdir "$WORK/lock"' EXIT
trap 'exit 1' HUP INT TERM
[ -e "$TXN" ] || exit 0
[ -d "$KIT" ] && [ ! -L "$KIT" ] || exit 1
[ "$(getprop ro.build.product)" = NEEV.KOR.0000.V039.001.260902 ] || exit 1
[ "$(getprop sys.boot_completed)" = 1 ] || exit 1
[ "$(hmgapid version)" = 'hmgapid 1.0.2' ] || exit 1
if [ -z "${BOOTCLASSPATH:-}" ]; then
    while read command name value; do [ "$command" != export ] || export "$name=$value"; done < /init.environ.rc
fi
cli() (
    cp=$1; class=$2; shift 2
    export CLASSPATH="$cp" ANDROID_ROOT=/system ANDROID_DATA=/data
    exec /system/bin/app_process.hmgapi-stock -Xint /system/bin "$class" "$@"
)
helper() { cli "$KIT/star-options.jar" kr.ioniq5.staroptions.StarOptions "$@"; }
engine() { cli /system/framework/hmgapi-hook.jar hmgapi.engine.Engine "$@"; }
state=$(helper status "$TXN") || exit 1
case "$state" in
    ABSENT) exit 0 ;;
    RESTORED) engine publish-state || exit 1; exit 0 ;;
    COMMITTED|PUBLISHED|PREPARED) ;;
    *) exit 1 ;;
esac
keys=$(cli "$KIT/star-options.jar" kr.ioniq5.staroptions.StarState) || exit 1
case "$keys" in
    *hmgapi.star.neev-star-precondition.precondition*)
        echo 'STAR_REMOVE_BLOCKED: select another function for both star buttons first.'
        exit 1 ;;
esac
helper rollback "$TXN" || exit 1
engine publish-state || { echo STAR_REMOVE_PUBLISH_FAILED; exit 1; }
sync
echo STAR_REMOVED_REBOOT_REQUIRED
