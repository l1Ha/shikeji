#!/bin/bash
# 构建 macOS 应用包：swiftc 编译（arm64+x86_64） -> .app Bundle -> ad-hoc 签名 -> zip
set -euo pipefail
cd "$(dirname "$0")/.."

APP_NAME="Shikeji.app"
APP_VERSION="1.0.0"
BUILD_DIR=$(mktemp -d)

SOURCES=(Sources/ShikejiKit/*.swift Sources/ShikejiApp/*.swift)
FRAMEWORKS=(-framework SwiftUI -framework AppKit -framework UserNotifications)

echo "[1/6] swiftc 编译 arm64 ..."
swiftc -O -parse-as-library -target arm64-apple-macosx13.0 \
    "${SOURCES[@]}" "${FRAMEWORKS[@]}" \
    -o "$BUILD_DIR/Shikeji.arm64"

echo "[2/6] swiftc 编译 x86_64（可选）..."
if swiftc -O -parse-as-library -target x86_64-apple-macosx13.0 \
    "${SOURCES[@]}" "${FRAMEWORKS[@]}" \
    -o "$BUILD_DIR/Shikeji.x86_64" 2>/dev/null; then
    lipo -create "$BUILD_DIR/Shikeji.arm64" "$BUILD_DIR/Shikeji.x86_64" -output "$BUILD_DIR/Shikeji"
    echo "    通用二进制（Universal 2）完成"
else
    cp "$BUILD_DIR/Shikeji.arm64" "$BUILD_DIR/Shikeji"
    echo "    x86_64 不可用，使用 arm64 单架构"
fi

echo "[3/6] 组装 $APP_NAME ..."
rm -rf "$APP_NAME"
mkdir -p "$APP_NAME/Contents/MacOS" "$APP_NAME/Contents/Resources"
cp "$BUILD_DIR/Shikeji" "$APP_NAME/Contents/MacOS/Shikeji"

cat > "$APP_NAME/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>Shikeji</string>
    <key>CFBundleIdentifier</key>
    <string>com.lihao.shikeji</string>
    <key>CFBundleName</key>
    <string>Shikeji</string>
    <key>CFBundleDisplayName</key>
    <string>时刻计</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>${APP_VERSION}</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSMinimumSystemVersion</key>
    <string>13.0</string>
    <key>NSPrincipalClass</key>
    <string>NSApplication</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>LSApplicationCategoryType</key>
    <string>public.app-category.healthcare-fitness</string>
</dict>
</plist>
PLIST

printf 'APPL????' > "$APP_NAME/Contents/PkgInfo"

if [ -f "Resources/AppIcon.icns" ]; then
    cp "Resources/AppIcon.icns" "$APP_NAME/Contents/Resources/AppIcon.icns"
    /usr/libexec/PlistBuddy -c "Add :CFBundleIconFile string AppIcon" "$APP_NAME/Contents/Info.plist" 2>/dev/null || true
fi

echo "[4/6] ad-hoc 签名..."
codesign --force --deep -s - "$APP_NAME"

echo "[5/6] 自检启动（3 秒后自动结束）..."
"$APP_NAME/Contents/MacOS/Shikeji" & APP_PID=$!
sleep 3
if kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID" 2>/dev/null || true
    echo "    启动自检通过"
else
    echo "    警告：应用提前退出，请检查日志"
    exit 1
fi

echo "[6/6] 打包 zip..."
ZIP_NAME="shikeji-mac-${APP_VERSION}.zip"
rm -f "$ZIP_NAME"
ditto -c -k --keepParent "$APP_NAME" "$ZIP_NAME"
echo "完成：$ZIP_NAME"
