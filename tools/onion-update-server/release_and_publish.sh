#!/bin/sh
set -eu

release_type=${1:-security}
release_notes=${2:-Correções e melhorias.}
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)

: "${NULLCHAT_UPDATE_ONION_HOST:?Defina NULLCHAT_UPDATE_ONION_HOST com o hostname onion v3}"
: "${NULLCHAT_UPDATE_PUBLIC_DIR:?Defina NULLCHAT_UPDATE_PUBLIC_DIR com o diretório publicado}"

case "$release_type" in
    major|minor|security) ;;
    *)
        echo "Tipo inválido: use major, minor ou security" >&2
        exit 2
        ;;
esac

cd "$project_dir"
./gradlew assembleRelease \
    "-PreleaseType=$release_type" \
    "-PappUpdateOnionHost=$NULLCHAT_UPDATE_ONION_HOST"

major=$(sed -n 's/^major=//p' version.properties)
minor=$(sed -n 's/^minor=//p' version.properties)
patch=$(sed -n 's/^patch=//p' version.properties)
version_name="$major.$minor.$patch"
version_code=$((major * 1000000 + minor * 1000 + patch))
apk_path="$project_dir/app/build/outputs/apk/release/app-release.apk"

python3 "$project_dir/tools/onion-update-server/publish_release.py" \
    --apk "$apk_path" \
    --public-dir "$NULLCHAT_UPDATE_PUBLIC_DIR" \
    --onion-host "$NULLCHAT_UPDATE_ONION_HOST" \
    --version-code "$version_code" \
    --version-name "$version_name" \
    --release-notes "$release_notes"
