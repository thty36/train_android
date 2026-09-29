import qrcode

img = qrcode.make("http://192.168.3.10:18080/app-release.apk")
img.save("D:/project/train_android/apk_share/download-qr.png")
print("QR saved: apk_share/download-qr.png")
