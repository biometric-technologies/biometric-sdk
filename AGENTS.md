# AGENTS.md — biometric-sdk

Rules for anyone, human or coding agent, who changes this repository. When a rule here conflicts with a task description, stop and ask instead of picking one.

## What the SDK is

A Kotlin Multiplatform library for iris, face and fingerprint biometrics on Android, iOS and the JVM. JS and native C targets are on hold; do not add code or dependencies for them.

The SDK's own code is meant to be thin: reading and writing images, converting pixels into model tensors, running models through the TensorFlow Lite interpreter, and reading and writing standard templates. Heavy pixel work belongs in TensorFlow Lite, not in Kotlin loops.

## Before writing an algorithm

1. Look for a platform API first. Face detection on Android is ML Kit, on iOS it is Apple Vision; they are faster than anything we would write.
2. Then look for an open model that runs in TFLite (BlazeFace for faces on the JVM is the example). Check its licence and the licence of the data it was trained on: both must allow commercial use. Record both in a `NOTICE.md` next to the model file.
3. If neither exists, keep or write the algorithm in code and say in the PR why. Where it is slow, move it into a fixed-weight TFLite graph: the same algorithm written as TFLite operations (convolutions, ring kernels, gathers), with no training and no data. Heavy pixel loops in Kotlin run on one thread without SIMD; a graph in TFLite gets threads and hardware delegates.

Exceptions that stay in code: anything that must produce or read an ISO/IEC 19794 record (19794-2 minutiae, 19794-6 iris), because interoperability with other systems depends on it, and the matchers that compare those templates.

## Models

- Every operation that uses a model takes its model from configuration (`ModelSource`): the built-in default or the caller's own.
- A model is loaded through `ResourceIO`: classpath or Android assets, a file path, or an `https:` URL that is downloaded once and cached on the device. Downloaded models need a checksum in the configuration.
- Built-in models must be small. Anything over a few megabytes is downloaded, not bundled.

## Dependencies

Do not add a dependency that pulls native binaries for every platform (OpenCV was removed in #31 for this reason: one face detector pulled in 17 platforms of natives, OpenBLAS and CPython). Before adding one, run `./gradlew dependencies` and list in the PR what it brings in.

## Tests

- Pure logic goes in `commonTest` and runs on every target.
- Tests that need TFLite natives go in `jvmTest` and skip on platforms without natives.
- A change visible to SDK users is also checked against `biometric-sdk-samples` (dispatch its workflow with `sdk_ref` set to your branch).
- Coverage: `./gradlew koverLog` prints line coverage per package; CI puts it in the job summary.

## Branches and pull requests

- Work branches start from `iris-v2` and go back into it; releases go from `release/x.y.z` into `master`.
- One change per pull request. The title says what now behaves differently ("X does Y instead of Z"), not which file changed.
- Request `sgornostal` as reviewer on every pull request.
- Merging: a pull request may be merged by its author or by an agent when all of these hold:
  - its base is an `iris-*` branch (never `master`: a merge there publishes a release, and that stays with a person);
  - `sgornostal`'s latest review is an approval of the current head commit, or the only commits after the approved one merge the base branch in, conflicts resolved without other changes;
  - the checks (`build`, `jvm-android`) are green on the current head;
  - it is not a draft (mark an approved draft ready first) and it does not wait on an unmerged pull request it depends on.
  Merge with a merge commit and keep the branch, so pull requests that depend on it can be moved to the new base. Then retarget those pull requests to the base the merged one went into.
- Nobody closes others' pull requests or force-pushes shared branches.
- Commit messages in English, imperative, no AI attribution trailers.

## Never

- Commit images of real people's eyes, faces or fingers, or any dataset whose licence forbids redistribution (CASIA, NIST SD, FVC). Tests use synthetic images.
- Put benchmark numbers from licensed datasets in the repository.
- Commit secrets, signing keys or tokens.
