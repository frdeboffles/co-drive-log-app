# Releasing

This project publishes a signed release APK through GitHub Actions when a GitHub Release is published.

## 1) Verify release signing secrets (one-time setup)

Configure these repository secrets:

- `CDL_RELEASE_STORE_B64`
- `CDL_RELEASE_STORE_PASSWORD`
- `CDL_RELEASE_KEY_ALIAS`
- `CDL_RELEASE_KEY_PASSWORD`

## 2) Bump app version

Edit `app/build.gradle.kts` in `defaultConfig`:

- Increment `versionCode` by 1
- Set `versionName` to the new release version (for example `1.1.0`)

Commit and push the version bump.

## 3) Ensure CI is green

The `Android CI` workflow runs on pushes and PRs. Confirm it passes before cutting a release.

## 4) Create and publish the GitHub release

Create a tag that matches `versionName` with a `v` prefix (example: `v1.1.0`) and publish the release.

Example:

```bash
gh release create v1.1.0 --target main --generate-notes
```

Publishing the release triggers `.github/workflows/release-apk.yml`.

## 5) Verify APK output

After workflow completion:

- Confirm workflow `Release APK` is green
- Confirm `app-release.apk` appears in release assets
- Install and smoke-test on device
