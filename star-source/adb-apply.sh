#!/system/bin/sh
# Exact-target, staged ADB operation; no key assignment or UI action.
set -u
export PATH=/system/bin:/system/xbin:/data/nemesis/bin
unset LD_PRELOAD HMGAPI_SELFTEST HMGAPI_ROOT HMGAPI_HOST HMGAPI_PRODUCT HMGAPI_ONEBIN HMGAPI_PROPS HMGAPI_TEST_ANY_CAR HMGAPI_LPATCH HMGAPI_HMGAPID
umask 077
PACK=/data/local/tmp/neev-star-options-20261110
WORK=/data/nemesis/neev-star-options
KIT=$WORK/kit-0.1.0
TXN=$WORK/transaction
UUID=8843fd55-d673-8045-a574-11e4e09794b0
MANIFEST=@MANIFEST_MD5@
fail() { echo "STAR_APPLY_ERROR $1"; exit 4; }
regular() { [ -f "$1" ] && [ ! -L "$1" ]; }
absent() { [ ! -e "$1" ] && [ ! -L "$1" ]; }
hash() { set -- $(md5 "$1"); echo "$1"; }
check_kit() {
    [ -d "$1" ] && [ ! -L "$1" ] && regular "$1/MANIFEST.md5" && [ "$(hash "$1/MANIFEST.md5")" = "$MANIFEST" ] || return 1
    while read digest path; do
        case "$path" in ''|/*|*..*|.*|*/.*|*[!A-Za-z0-9_./-]*) return 1;; esac
        regular "$1/$path" && [ "$(hash "$1/$path")" = "$digest" ] || return 1
    done < "$1/MANIFEST.md5"
}
case "$(id)" in 'uid=0('*|'uid=0 '*|'uid=0') ;; *) fail ROOT_REQUIRED;; esac
[ "$(getprop ro.build.product)" = NEEV.KOR.0000.V039.001.260902 ] && [ "$(getprop ro.board.platform)" = tcc803x ] && [ "$(getprop ro.build.version.sdk)" = 19 ] || fail WRONG_UNIT
[ "$(getprop sys.boot_completed)" = 1 ] || fail BOOT_NOT_READY
[ "$(hmgapid version)" = 'hmgapid 1.0.2' ] || fail HMG_VERSION
regular /data/hmgapi/hook/enable && absent /data/hmgapi/hook/disable && absent /data/hmgapi/state/journal.json || fail HMG_NOT_READY
check_kit "$PACK" || fail STAGED_KIT_MISMATCH
if [ -z "$BOOTCLASSPATH" ]; then
    while read command name value; do [ "$command" != export ] || export "$name=$value"; done < /init.environ.rc
fi
if absent "$WORK"; then mkdir "$WORK" || fail WORK_CREATE; fi
[ -d "$WORK" ] && [ ! -L "$WORK" ] && chmod 700 "$WORK" || fail WORK_UNSAFE
mkdir "$WORK/lock" || fail OPERATION_LOCKED
trap 'rmdir "$WORK/lock"' EXIT
trap 'exit 4' HUP INT TERM
if absent "$KIT"; then
    stage=$WORK/stage-$$
    absent "$stage" && mkdir "$stage" || fail STAGE_CREATE
    while read digest path; do
        case "$path" in */*) mkdir -p "$stage/${path%/*}" || fail STAGE_COPY;; esac
        cat "$PACK/$path" > "$stage/$path" || fail STAGE_COPY
    done < "$PACK/MANIFEST.md5"
    cat "$PACK/MANIFEST.md5" > "$stage/MANIFEST.md5" && check_kit "$stage" && mv "$stage" "$KIT" || fail STAGE_VERIFY
fi
check_kit "$KIT" || fail PRIVATE_KIT_MISMATCH
cli() (
    cp=$1; class=$2; shift 2
    export CLASSPATH="$cp" ANDROID_ROOT=/system ANDROID_DATA=/data
    exec /system/bin/app_process.hmgapi-stock -Xint /system/bin "$class" "$@"
)
helper() { cli "$KIT/star-options.jar" kr.ioniq5.staroptions.StarOptions "$@"; }
engine() { cli /system/framework/hmgapi-hook.jar hmgapi.engine.Engine "$@"; }
rollback_failure() {
    if helper rollback "$TXN"; then engine publish-state || :; fail "$1"_RESTORED; fi
    fail "$1"_RECOVERY_REQUIRED
}
state=$(helper status "$TXN") || fail TRANSACTION_UNREADABLE
case "$state" in
    COMMITTED) helper installed "$TXN" && helper apps || fail INSTALLED_STATE_CHANGED; echo ALREADY_INSTALLED; exit 0;;
    PREPARED|PUBLISHED) helper rollback "$TXN" && engine publish-state || fail INTERRUPTED_RECOVERY;;
    ABSENT|RESTORED) ;;
    *) fail UNKNOWN_TRANSACTION;;
esac
helper probe "$KIT/payload" || fail PROBE_FAILED
cli /system/framework/hmgapi-hook.jar hmgapi.engine.Signing verify "$KIT/payload/mod" "$UUID" || fail SIGNATURE_FAILED
cli "$KIT/star-options.jar" kr.ioniq5.staroptions.StarState > "$WORK/assignments.before" || fail KEY_STATE_UNAVAILABLE
cat "$WORK/assignments.before"
helper prepare "$KIT/payload" "$TXN" || fail PREPARE_FAILED
helper publish "$TXN" || rollback_failure PUBLISH_FAILED
engine verify-map || rollback_failure VERIFY_FAILED
map_state=$(engine map) || rollback_failure MAP_STATUS_FAILED
echo "$map_state"
case "$map_state" in *'hooks on'*) ;; *) rollback_failure HOOKS_NOT_READY;; esac
engine install "$KIT/payload/mod" --uuid "$UUID" --from-nemesis || rollback_failure MOD_INSTALL_FAILED
helper commit "$TXN" || rollback_failure COMMIT_FAILED
cli "$KIT/star-options.jar" kr.ioniq5.staroptions.StarState > "$WORK/assignments.after" || fail POST_KEY_STATE_UNAVAILABLE
cat "$WORK/assignments.after"
[ "$(hash "$WORK/assignments.before")" = "$(hash "$WORK/assignments.after")" ] || { echo KEY_STATE_CHANGED; exit 5; }
sync
echo STAR_CHOICES_INSTALLED_REBOOT_REQUIRED
