🕐 KootPanKing — Analog Clock + PC Remote Control via Telegram

Control your PC from anywhere through any router —
no port forwarding, no VPN, just Telegram.

이미지 표시
이미지 표시
이미지 표시

🚀 What is KootPanKing?

KootPanKing is a Windows desktop clock app that doubles as a
full PC remote control system via Telegram bot.
It stays running in the system tray — so your Telegram bot is
always alive, always ready.

✨ Key Features

🤖 Telegram Remote Control
CommandDescription/captureCapture clock screen → send to Telegram/sFull screen capture → send to Telegram/c1~/c4Capture specific monitor (1~4)/cmdExecute DOS/CMD commands remotely/psExecute PowerShell commands remotely/downShutdown PC remotely (with confirmation)/rebootReboot PC remotely (with confirmation)/whGet PC info (IP, OS, username)/textSend text → auto-save to PC/saveSave files to PC remotely/msQuery Google Calendar schedule/nsQuery Naver Calendar schedule/hShow command list
🕐 Analog Clock

Beautiful analog clock always visible in system tray
Always-on-top display option
World clock support (15 cities)
Chime & alarm system

📡 Integrations

Google Calendar
Naver Calendar
Gmail
KakaoTalk message mirroring
CCTV / YouTube live background feed
Windows auto-start
Auto-update system

> ⚠️ Current UI language: Korean only.
> 
> English UI is planned for the next major release.  
> All Telegram commands work in English.

💡 Why Telegram?

Most remote control tools require:
OthersKootPanKing❌ Port forwarding✅ Zero network config❌ VPN setup✅ Works behind any router❌ Static IP required✅ Works from anywhere❌ Paid subscription✅ Free (Telegram is free)
KootPanKing uses Telegram bot as a secure relay —
no server, no cloud, your PC talks directly to your phone.

📦 Installation

Download KootPanKing.zip from Releases
Before extracting: Right-click ZIP → Properties →
Check "Unblock" → Apply
Extract and run KootPanKing.exe


⚠️ Windows SmartScreen may warn on first run.

Click "More info" → "Run anyway"
Source code is fully open — build it yourself if unsure.


🔨 Build from Source

bash# Requirements: JDK 17+
# Clone this repository, then:


_ReleaseBLD_All.bat


# Output: dist\KootPanKing\KootPanKing.exe


🤖 Telegram Bot Setup (5 minutes)


Open Telegram → search @BotFather
Send /newbot → follow instructions → copy the token
Send any message to your new bot
Visit https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates
Find your Chat ID in the response
Enter token + Chat ID in KootPanKing settings → Done ✅


🛡️ Security

All commands are restricted to your Chat ID only
Dangerous commands (/down, /reboot) require confirmation
Source code is fully open for inspection
No data is sent to any third-party server


📬 Contact

📧 Email: garpsu@naver.com
📝 Blog: https://blog.naver.com/garpsu


⭐ If this helped you, please give it a Star!

It helps others discover this project.
