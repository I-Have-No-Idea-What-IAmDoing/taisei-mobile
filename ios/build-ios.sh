#!/bin/sh
set -e

# Build script driven by Xcode or standalone CI
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

TARGET_SDK="${1:-iphoneos}"
BUILD_DIR="$ROOT_DIR/build-ios-$TARGET_SDK"

if [ "$TARGET_SDK" = "iphonesimulator" ]; then
    CROSS_FILE="$SCRIPT_DIR/cross/ios-simulator.ini"
else
    CROSS_FILE="$SCRIPT_DIR/cross/ios-arm64.ini"
fi

if [ ! -f "$BUILD_DIR/build.ninja" ]; then
    meson setup "$BUILD_DIR" \
        --cross-file "$CROSS_FILE" \
        -Dallocator=libc \
        -Dr_gl33=disabled \
        -Dr_sdlgpu=enabled \
        -Dpackage_data=false \
        -Dinstall_relocatable=enabled
fi

ninja -C "$BUILD_DIR"
echo "iOS build complete: $BUILD_DIR/src/libtaisei.a"
