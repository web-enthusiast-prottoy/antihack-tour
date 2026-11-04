# Antihack Tour

Native Android tournament app built with Kotlin, Jetpack Compose and Firebase.

The application source, Firebase rules/tests, operator guides and build scripts are in [app](app/). Start with [app/README.md](app/README.md), the [production release guide](app/PRODUCTION-RELEASE.md) and the [implementation plan](APP-IMPLEMENTATION-PLAN.md).

## Local setup

Install Java and the Android SDK, configure `app/android/local.properties` for your machine, and install the Firebase tooling dependencies with `npm ci` in `app/admin`. See [local player setup](app/LOCAL-PLAYER.md) for emulator instructions. Run `app/scripts/build.sh local` to build and check the local app.

## Live configuration

Provide your own Firebase Android configuration at `app/android/app/google-services.json` before building live. Firebase configuration, signing keys/passwords, SDK paths, dependency directories, generated APKs and production verification/data logs are deliberately excluded from Git. Keep the production `.signing` folder securely backed up; it is required to sign compatible updates. See [production release instructions](app/PRODUCTION-RELEASE.md) for the complete toolchain and signing requirements.

The REA source and reverse-engineering workspace are separate from this application and are not included.
