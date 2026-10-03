# DZ Guide AI

Android-first Algerian document and object intelligence assistant.

Scan receipts, book pages, forms, civil-status documents, identity documents, barcodes and everyday objects. The app combines on-device OCR/vision with an Algerian procedure knowledge layer. For administrative guidance it points to official sources and distinguishes online from in-person next steps.

## Accuracy
No AI can honestly guarantee perfect recognition of every object or every changing government procedure. DZ Guide AI therefore exposes confidence, requests better evidence when needed, and requires official-source verification for administrative actions.

## Current official seed
The Ministry of Interior portal currently lists biometric-document services, online civil-status services, remote administrative submissions and other citizen services: https://services.interieur.gov.dz/

## Build
Pushing to main triggers GitHub Actions. The workflow builds a debug APK and uploads it as an artifact.

## Roadmap
- richer Algerian procedure database with source dates
- book catalog lookup and ISBN support
- receipt field extraction and warranty reminders
- Arabic/French/English UI
- encrypted local scan history
- optional server AI reasoning layer
- release signing and Play Store bundle
