# OpenFit Android

A personal health dashboard for Android that reads your data from Health Connect: steps, sleep, heart rate, workouts, and more. Puts it somewhere more useful than the default app.

Pairs with wearables that already sync to Health Connect (Pixel Watch, Fitbit, Samsung Galaxy Watch, etc). No new accounts, no cloud sync, no data leaving your phone.

## Requirements

- Android 12+ (API 31)
- Health Connect installed and set up
- Wearable or fitness app already writing data to Health Connect

## Features

- Today overview with hourly step chart, sleep stages, and vitals
- 14-day trends for steps, heart rate, HRV, weight, and more
- AI coach (bring your own API key: Claude, GPT, Gemini, or any OpenAI-compatible endpoint)
- Manual logging for water, food, weight, and workouts
- Body metrics: weight, body fat, BMI, hydration, BMR
- Reproductive health tracking (from Health Connect data)
- Writes back to Health Connect: weight, water, workouts, height, nutrition

## Building

Standard Android project. Open in Android Studio or build from the command line:

```bash
./gradlew assembleDebug
```

Requires Android SDK with compile SDK 36. Java 17.

## Setup

1. Install the app
2. Open it and grant Health Connect permissions
3. It degrades gracefully if some permissions are skipped
4. To use the AI coach, go to Settings and add your API key

## AI Coach

The coach sends your health summary (today's data + 14-day averages) to whichever AI you configure. It works with:

- Claude (Anthropic)
- GPT (OpenAI)
- Gemini (Google)
- Any self-hosted OpenAI-compatible endpoint (Ollama, LM Studio, etc.)
- Custom endpoints with a simple message/response format

Nothing is sent without you actively opening the Coach tab and sending a message. The daily summary feature is optional and off by default.

## Releases

Pre-built APKs are attached to each [release](../../releases). The workflow builds from source on every version tag.

## Notes

Health Connect battery levels work for devices that implement the standard Bluetooth Battery Service profile. Pixel Watch does; Fitbit uses a proprietary protocol so it won't show.

Steps and activity use Health Connect's aggregation API, which deduplicates across sources the same way the Google Health app does. If your watch and phone both report steps, you won't see them doubled.
