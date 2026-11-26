#!/bin/sh
set -eu

cd "$(dirname -- "$0")"

case "$(uname -s)" in
    Darwin)
        task=packageDmg
        format=dmg
        opener=open
        ;;
    Linux)
        task=packageDeb
        format=deb
        opener=xdg-open
        ;;
    *)
        printf '%s\n' 'Unsupported host. On Windows, run build-and-open-installer.bat.' >&2
        exit 1
        ;;
esac

./gradlew ":platform:desktop:$task"

package_dir="$PWD/platform/desktop/build/compose/binaries/main/$format"
set -- "$package_dir"/*."$format"
if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
    printf 'Expected one .%s installer in %s\n' "$format" "$package_dir" >&2
    exit 1
fi

printf 'Opening installer: %s\n' "$1"
exec "$opener" "$1"
