# Private DNS Shortcut (Tile)

A simple, lightweight Android utility built in 10 minutes to toggle the system's **Private DNS** on and off directly from the Quick Settings panel (like Wi-Fi or Bluetooth). 

This app works **without root** and connects natively via Android's DoT (DNS over TLS) protocol, saving battery compared to traditional local VPN-based alternatives.

## Features
- **Instant Toggle:** One-tap switch from the notification shade.
- **Battery Friendly:** Runs for a millisecond to change settings, then closes. No background services.
- **VPN Compatible:** Since it uses the native DNS settings, you can run a real VPN alongside it.
- **Default Server:** Pre-configured for `dns.adguard.com` (can be modified in the source code).

## How to Setup (ADB Permission Required)

Because Android protects secure system settings, you must grant a specific permission to this app via a computer once after installation.

1. Install the APK on your device.
2. Enable **USB Debugging** in your Android's Developer Options.
3. Connect your phone to your computer.
4. Open a terminal/CMD on your computer and run:

```bash
adb shell pm grant com.aistudio.privatedns.qxtile android.permission.WRITE_SECURE_SETTINGS
```

5. Open the Quick Settings panel on your phone, edit the buttons, and drag the **Private DNS Tile** to your active panel.

## License
MIT License. Feel free to clone, edit, or improve!
