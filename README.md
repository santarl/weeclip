> **AI assistance:** this project is written with the help of Claude (Anthropic). Commits carry a `Co-Authored-By` trailer.

# weeclip

A tiny Android app for saving YouTube Shorts / Reels / TikToks.

Create groups inside the app; each one shows up as a "contact" in the system share sheet (Android 10+).
Share a link to it and it is saved instantly with no UI, then the title, author and thumbnail are fetched in the background.
Sharing to plain "weeclip" saves to the Inbox.

No AndroidX, no libraries, no analytics. The only permission is `INTERNET`, used to fetch link metadata.

Optional WhatsApp relay (work in progress): with notification access granted, weeclip keeps each WhatsApp chat's inline Reply action in memory so a message can be sent without opening WhatsApp. It only looks at WhatsApp notifications, never reads message text, stores nothing on disk and makes no network requests.

Licensed under GPL-3.0.
