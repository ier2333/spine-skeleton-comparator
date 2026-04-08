#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
UPSTREAM_DIR="${SPINE_LIBGDX_DIR:-"${REPO_DIR}/../spine-runtimes/spine-libgdx"}"
UPSTREAM_SPINE_DIR="${UPSTREAM_DIR}/spine-skeletonviewer/src/com/esotericsoftware/spine"
UPSTREAM_SKIN_DIR="${UPSTREAM_DIR}/spine-skeletonviewer/assets/skin"
OVERLAY_SPINE_DIR="${REPO_DIR}/overlay/com/esotericsoftware/spine"
UPSTREAM_OVERLAY_DIR="${REPO_DIR}/upstream-overlay/spine-libgdx"

if [[ ! -d "${UPSTREAM_DIR}" ]]; then
  echo "Upstream spine-libgdx directory not found: ${UPSTREAM_DIR}" >&2
  echo "Set SPINE_LIBGDX_DIR to your upstream spine-libgdx path." >&2
  exit 1
fi

mkdir -p "${UPSTREAM_SPINE_DIR}" "${UPSTREAM_SKIN_DIR}"

cp "${OVERLAY_SPINE_DIR}/SkeletonComparator.java" "${UPSTREAM_SPINE_DIR}/SkeletonComparator.java"
cp "${OVERLAY_SPINE_DIR}/SkeletonComparatorUI.java" "${UPSTREAM_SPINE_DIR}/SkeletonComparatorUI.java"
cp "${OVERLAY_SPINE_DIR}/SkeletonComparatorLoader.java" "${UPSTREAM_SPINE_DIR}/SkeletonComparatorLoader.java"
cp "${OVERLAY_SPINE_DIR}/SkeletonComparatorDiff.java" "${UPSTREAM_SPINE_DIR}/SkeletonComparatorDiff.java"
cp "${UPSTREAM_OVERLAY_DIR}/spine-skeletonviewer/src/com/esotericsoftware/spine/SkeletonViewerAtlas.java" "${UPSTREAM_SPINE_DIR}/SkeletonViewerAtlas.java"
cp "${UPSTREAM_OVERLAY_DIR}/build.gradle" "${UPSTREAM_DIR}/build.gradle"
cp "${UPSTREAM_OVERLAY_DIR}/spine-skeletonviewer/assets/skin/skin.json" "${UPSTREAM_SKIN_DIR}/skin.json"
cp "${UPSTREAM_OVERLAY_DIR}/spine-skeletonviewer/assets/skin/skin.atlas" "${UPSTREAM_SKIN_DIR}/skin.atlas"
cp "${UPSTREAM_OVERLAY_DIR}/spine-skeletonviewer/assets/skin/skin.png" "${UPSTREAM_SKIN_DIR}/skin.png"
cp "${UPSTREAM_OVERLAY_DIR}/spine-skeletonviewer/assets/skin/font-calibri-12.fnt" "${UPSTREAM_SKIN_DIR}/font-calibri-12.fnt"
cp "${UPSTREAM_OVERLAY_DIR}/spine-skeletonviewer/assets/skin/font-calibri-12.png" "${UPSTREAM_SKIN_DIR}/font-calibri-12.png"
cp "${UPSTREAM_OVERLAY_DIR}/spine-skeletonviewer/assets/skin/NotoSansSC-Regular.otf" "${UPSTREAM_SKIN_DIR}/NotoSansSC-Regular.otf"

(
  cd "${UPSTREAM_DIR}"
  ./gradlew :spine-skeletonviewer:comparatorJar
  java -jar "spine-skeletonviewer/build/libs/spine-skeletonviewer-4.2.13-SNAPSHOT-comparator.jar" "$@"
)
