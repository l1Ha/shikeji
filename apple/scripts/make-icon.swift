// 生成 1024x1024 应用图标 PNG（青绿底 + 白色呼吸圆环），随后由 iconutil 转 icns
import CoreGraphics
import ImageIO
import Foundation
import UniformTypeIdentifiers

let size = 1024
let colorSpace = CGColorSpaceCreateDeviceRGB()
let context = CGContext(
    data: nil, width: size, height: size,
    bitsPerComponent: 8, bytesPerRow: 0,
    space: colorSpace,
    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
)!

let bounds = CGRect(x: 0, y: 0, width: size, height: size)
let rounded = CGPath(roundedRect: bounds, cornerWidth: 180, cornerHeight: 180, transform: nil)
context.addPath(rounded)
context.setFillColor(CGColor(red: 0, green: 0.808, blue: 0.788, alpha: 1))
context.fillPath()

// 外圈（半透明）与内圆（实白），呼应呼吸圆圈动画
context.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 0.35))
context.fillEllipse(in: CGRect(x: 232, y: 232, width: 560, height: 560))
context.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 0.92))
context.fillEllipse(in: CGRect(x: 372, y: 372, width: 280, height: 280))

guard let image = context.makeImage() else { fatalError("makeImage failed") }
let output = URL(fileURLWithPath: FileManager.default.currentDirectoryPath).appendingPathComponent("AppIcon1024.png")
let destination = CGImageDestinationCreateWithURL(output as CFURL, UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(destination, image, nil)
guard CGImageDestinationFinalize(destination) else { fatalError("write failed") }
print("图标已生成: \(output.path)")
