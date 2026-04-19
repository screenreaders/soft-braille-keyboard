Sentry And GitHub Setup
=======================

This project can collect crashes and manual support reports in the Android app
and turn them into GitHub issues, but the final automation requires a Sentry
project configured outside the APK.

What The App Already Does
-------------------------

* Initializes Sentry when `sentryDsn` or `SENTRY_DSN` is provided at build time.
* Sends uncaught crashes to Sentry.
* Provides an in-app "Support report" form.
* Attaches app, IME and braille diagnostics to manual reports.
* Falls back to opening a prefilled GitHub issue and copying diagnostics to the
  clipboard when Sentry is not configured.

Build-Time Configuration
------------------------

Provide the DSN with either:

```
export SENTRY_DSN=https://examplePublicKey@o0.ingest.sentry.io/0
```

or in `~/.gradle/gradle.properties`:

```
sentryDsn=https://examplePublicKey@o0.ingest.sentry.io/0
```

Then rebuild:

```
./gradlew :app:assembleDebug
```

Sentry Project Setup
--------------------

You can create the project manually in the Sentry UI or with the helper script:

```
export SENTRY_AUTH_TOKEN=...
export SENTRY_ORG_SLUG=...
export SENTRY_TEAM_SLUG=...
./scripts/create_sentry_project.sh soft-braille-keyboard "Soft Braille Keyboard"
```

The script creates the Android project if needed and prints the public DSN.

Manual setup:

1. Create an Android project in Sentry.
2. Add the GitHub integration in Sentry and connect it to:

   `screenreaders/soft-braille-keyboard`

3. In Sentry, create issue alert rules such as:

   * `event.type:error` -> create a GitHub issue
   * `tags.report_source:manual_feedback` -> create a GitHub issue with label
     `hardware`

4. Optionally enable assignment rules or labels for:

   * `hardware`
   * `braille-display`
   * `regression`

Recommended Tags And Grouping
-----------------------------

Manual reports from the app include tags such as:

* `application=soft-braille-keyboard`
* `app_version=<version>`
* `report_source=manual_feedback`
* `has_diagnostics=true|false`
* `report_subject=<normalized-title>`

This makes it practical to route:

* crashes to general bug issues,
* hardware reports to braille-display issues,
* regressions to dedicated GitHub issues.

Notes
-----

* Do not embed a GitHub token in the Android app.
* The DSN is enough for Sentry ingestion; GitHub issue creation should happen
  in Sentry's server-side integration.
* If Sentry is disabled, testers can still submit useful reports directly to
  GitHub using the in-app fallback path.
