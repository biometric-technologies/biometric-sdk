# About
Biometric SDK is complete open-source Software Development Kit for face, iris and fingerprint modalities.

![Workflow name](https://github.com/biometric-technologies/biometric-sdk/actions/workflows/release.yml/badge.svg)
[![Maven Central](https://img.shields.io/maven-central/v/net.iriscan/biometric-sdk.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22net.iriscan%22%20AND%20a:%22biometric-sdk%22)
[![Maven Central](https://img.shields.io/maven-central/v/net.iriscan/biometric-sdk-jvm.svg?label=Maven%20Central%20JVM)](https://search.maven.org/search?q=g:%22net.iriscan%22%20AND%20a:%22biometric-sdk-jvm%22)
[![Maven Central](https://img.shields.io/maven-central/v/net.iriscan/biometric-sdk-android.svg?label=Maven%20Central%20Android)](https://search.maven.org/search?q=g:%22net.iriscan%22%20AND%20a:%22biometric-sdk-android%22)
[![CocoaPods](https://img.shields.io/cocoapods/v/BiometricSdk)](https://cocoapods.org/pods/BiometricSdk)
[![GitHub release](https://img.shields.io/github/v/release/biometric-technologies/biometric-sdk)](https://GitHub.com/biometric-technologies/biometric-sdk/releases/)

# Structure

NOTE: Some modules are still under development.

* `io` – defines functionality for working with images and biometric files.
* `iris` – provides functionality for working with IRIS biometrics.
* `qualityControl` – (unavailable) provides functionality for image quality calculation.
* `face` – (unavailable) provides functionality for working with FACE biometrics.
* `finger` – (unavailable) provides functionality for working with FINGERPRINT biometrics.

# Initialize SDK
Call `BiometricSdkFactory.configure();` for initial SDK configuration.  
After that you can get SDK instance by calling `getInstance()` method.

# IO
## Image
### Read/Write image from file
```java
Image img = instance.io().readImage(path);
instance.io().write(img, "/opt/tmp", ImageFormat.PNG);
``` 
### Read/Write image from bytes array
```java
Image img = instance.io().readImage(bytes);
byte[] = instance.io().writeAsByteArrayImage(img, ImageFormat.PNG);
``` 

# Iris
The iris pipeline follows the classic Daugman approach:

1. **Segmentation.** The pupil and the limbus are located as two circles with
   independent centers using the integro-differential operator. Specular
   reflections are removed first, the pupil boundary must be present around most
   of the circle, and the limbus is searched in the lateral sectors that eyelids
   rarely cover. Upper and lower eyelids are detected and masked.
2. **Normalization.** The iris is unwrapped with the rubber-sheet model to a
   256 x 64 texture. Occluded samples (eyelids, eyelashes, reflections, image
   borders) are marked as invalid.
3. **Encoding.** Each radial band is filtered with a 1D log-Gabor filter and the
   phase is quantized to 2 bits, giving a 4096-bit iris code plus a mask of valid
   bits. Fragile bits (weak responses) are masked too.
4. **Matching.** Masked fractional Hamming distance, minimized over rotations of
   up to +-22 degrees and normalized for the number of compared bits. The score is
   `1 - HD`, so 1.0 is identical and about 0.5 is unrelated.

## Extract
Unwrap the iris of an eye image. Occluded pixels of the texture are set to -1.
```java
Image eye = instance.io().readImage(path);
Image texture = instance.iris().extractor().extract(eye);
```
## Encode
```java
byte[] template = instance.iris().encoder().extractAndEncode(eye);
byte[] fromTexture = instance.iris().encoder().encode(texture);
```
## Match
```java
boolean same = instance.iris().matcher().matches(template1, template2);   // 1:1
double score = instance.iris().matcher().score(template1, template2);     // 0.0-1.0
IrisMatch best = instance.iris().matcher().identify(probe, gallery);      // 1:N, null if no match
```
The default threshold is 0.67 (Hamming distance 0.33). The right value depends on
your sensors, population and the false match rate you can accept: measure it on
your own images with the benchmark below and set `IrisMatchProperties(threshold = ...)`.

## Accuracy benchmark
`IrisDatasetBenchmark` (JVM tests) enrolls every image of a dataset and reports
failure to enroll, genuine and impostor score distributions, EER and FNMR at fixed
FMR, and can write all scores and DET points to CSV:
```sh
IRIS_DATASET=/data/CASIA-Iris-Interval IRIS_REPORT=build/iris-report \
  ./gradlew jvmTest --tests '*IrisDatasetBenchmark*'
```
By default the identity is the parent folder of each image; set `IRIS_SUBJECT` to
a regex with one group to read it from the file path instead.

## Template format and upgrading from 0.6
Templates start with the `IR2` header, followed by the code and mask bit planes
(1032 bytes with default settings). Templates created by 0.6 and earlier can still
be compared with each other using the old method and `legacyThreshold`, but they
never match the new templates: re-enroll from the stored images to move to the new
algorithm.

Communication
-------------
If you found a bug and can provide steps to reliably reproduce it, or if you
have a feature request, please
[open an issue](https://github.com/biometric-technologies/biometric-sdk/issues). Other
questions may be addressed to the
[Biometric Technologies project maintainers](mailto:info@iriscan.net).

License
-------
Biometric SDK is released under the GNU General Public License v3.0. See the
[LICENSE](https://github.com/biometric-technologies/biometric-sdk/blob/master/LICENSE.md)
for details.


