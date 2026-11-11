# AGENTS.md — biometric-sdk

Rules for anyone, human or coding agent, who changes this repository. When a rule here conflicts with a task description, stop and ask instead of picking one.

## What the SDK is

A Kotlin Multiplatform library for iris, face and fingerprint biometrics on Android, iOS and the JVM. JS and native C targets are on hold; do not add code or dependencies for them.

The SDK's own code is meant to be thin: reading and writing images, converting pixels into model tensors, running models through the TensorFlow Lite interpreter, and reading and writing standard templates. Detection, segmentation and embeddings belong in models, not in hand-written algorithms.

## Before writing an algorithm

1. Look for a platform API first. Face detection on Android is ML Kit, on iOS it is Apple Vision; they are faster than anything we would write.
2. Then look for an open model that runs in TFLite (BlazeFace for faces on the JVM is the example). Check its licence and the licence of the data it was trained on: both must allow commercial use. Record both in a `NOTICE.md` next to the model file.
3. Write code only when neither exists, and say in the PR why. Heavy pixel loops in Kotlin run on one thread without SIMD; a model in TFLite gets threads and hardware delegates for free.

Exceptions that stay in code: anything that must produce or read an ISO/IEC 19794 record (19794-2 minutiae, 19794-6 iris), because interoperability with other systems depends on it, and the matchers that compare those templates.

## Models

- Every operation that uses a model takes its model from configuration: the built-in default or the caller's own.
- A model is loaded through `ResourceIO`: classpath or Android assets, a file path, or an `https:` URL that is downloaded once and cached on the device. Downloaded models need a checksum in the configuration.
- Built-in models must be small. Anything over a few megabytes is downloaded, not bundled.

## Dependencies

Do not add a dependency that pulls native binaries for every platform (OpenCV is being removed for this reason, see #31: one face detector pulled in 17 platforms of natives, OpenBLAS and CPython). Before adding one, run `./gradlew dependencies` and list in the PR what it brings in.

## Tests

- Pure logic goes in `commonTest` and runs on every target.
- Tests that need TFLite natives go in `jvmTest` and skip on platforms without natives (bytedeco 1.5.8 has none for macOS arm64).
- A change visible to SDK users is also checked against `biometric-sdk-samples` (dispatch its workflow with `sdk_ref` set to your branch).
- Coverage: `./gradlew koverLog` prints line coverage per package; CI puts it in the job summary.

## Branches and pull requests

- Work branches start from `iris-v2` and go back into it; releases go from `release/x.y.z` into `master`.
- One change per pull request. The title says what now behaves differently ("X does Y instead of Z"), not which file changed.
- Request `sgornostal` as reviewer on every pull request. The reviewer merges; authors and agents never merge, close others' PRs or force-push shared branches.
- Commit messages in English, imperative, no AI attribution trailers.

## Never

- Commit images of real people's eyes, faces or fingers, or any dataset whose licence forbids redistribution (CASIA, NIST SD, FVC). Tests use synthetic images.
- Put benchmark numbers from licensed datasets in the repository.
- Commit secrets, signing keys or tokens.
