> **AI assistance:** this project is written with the help of Claude (Anthropic). Commits carry a `Co-Authored-By` trailer.

# weeclip

A tiny Android app for saving YouTube Shorts / Reels / TikToks.

Create groups inside the app; each one shows up as a "contact" in the system share sheet (Android 10+).
Share a link to it and it is saved instantly with no UI, then the title, author and thumbnail are fetched in the background.
Sharing to plain "weeclip" saves to the Inbox.

No AndroidX, no libraries, no analytics. The only permission is `INTERNET`, used to fetch link metadata.

Licensed under GPL-3.0.
