# DZ Guide AI

A serious Android-first visual assistant for Algerian documents, receipts, books, forms, barcodes and everyday objects.

## What this build does
- Native Android + Jetpack Compose UI.
- CameraX live scanner.
- Latin + Arabic OCR through ML Kit.
- QR/barcode scanning.
- ML Kit image labels.
- Structured result cards with confidence.
- Receipt, book, civil-status, biometric identity/passport, tax and CNRC knowledge routes.
- Official-source-first guidance.
- Local scan history for the current session.
- GitHub Actions automatically builds a debug APK and uploads it as DZ-Guide-AI-debug.

## The important distinction
Algeria's official digital-service ecosystem is expanding quickly. The Interior Ministry currently advertises remote administrative submissions, online civil-status certificates and biometric-document tracking, and FADAAOCOM is an official public-service app. DZ Guide AI is therefore designed as the visual interpretation layer: scan something first, understand what it is, then route the user to the appropriate official service instead of pretending to be the government portal itself.

## Current official knowledge seeds
- Interior Ministry: https://services.interieur.gov.dz/
- DGI: https://www.mfdgi.gov.dz/fr/
- CNRC / Ministry of Commerce: https://commerce.gov.dz/fr/portail-du-cnrc

## Build artifact
Every push to main triggers .github/workflows/android.yml. When the run succeeds, GitHub uploads a downloadable artifact named DZ-Guide-AI-debug containing the APK.

This is a debug APK, not a Play Store-signed release. A production release should use a private signing key stored in GitHub Secrets and produce an AAB.

## Architecture
Camera -> CameraX -> Arabic OCR + Latin OCR + QR/barcode + image labels -> recognition/extraction -> Algerian knowledge routes -> official-source verification -> human-readable next steps.

## Accuracy
Government procedures, fees, addresses and portal URLs can change. Production knowledge should carry source and freshness metadata and never fabricate a missing requirement. Sensitive identity documents should be processed locally whenever possible and not uploaded without explicit user action.

## Next major layer
- ISBN/book metadata lookup.
- Real receipt field extraction and warranty reminders.
- Searchable, source-dated procedure database covering more ministries and public services.
- Freshness checks for official pages.
- Optional secure server AI for ambiguous cases.
- Arabic/French/English UI.
- Encrypted persistent scan history.
- Release signing + Play Store AAB.
