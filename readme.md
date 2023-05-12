# About
Biometric SDK is complete open-source solutions for different modals.

![Workflow name](https://github.com/biometric-technologies/biometric-sdk/actions/workflows/release.yml/badge.svg)
[![Maven Central](https://img.shields.io/maven-central/v/net.iriscan/biometric-sdk.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22net.iriscan%22%20AND%20a:%22biometric-sdk%22)
[![Maven Central](https://img.shields.io/maven-central/v/net.iriscan/biometric-sdk-jvm.svg?label=Maven%20Central%20JVM)](https://search.maven.org/search?q=g:%22net.iriscan%22%20AND%20a:%22biometric-sdk-jvm%22)
[![Maven Central](https://img.shields.io/maven-central/v/net.iriscan/biometric-sdk-android.svg?label=Maven%20Central%20Android)](https://search.maven.org/search?q=g:%22net.iriscan%22%20AND%20a:%22biometric-sdk-android%22)
[![CocoaPods](https://img.shields.io/cocoapods/v/BiometricSdk)](https://cocoapods.org/pods/BiometricSdk)
[![GitHub release](https://img.shields.io/github/v/release/biometric-technologies/biometric-sdk)](https://GitHub.com/biometric-technologies/biometric-sdk/releases/)

# Structure

NOTE: Some modules are still unavailable.

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
## Extract
Operations for extracting iris texture from eye image.
### Extract iris texture from image
```java
Image eye = instance.io().readImage(path);
Img texture = instance.iris().extractor().extract(eye);
``` 
## Encode
Operations for encoding iris texture to binary template.
### Extract iris texture from image
```java
byte[] template = instance.iris().encoder().encode(img);
```
### Extracts and encodes iris texture from image
```java
byte[] template2 = instance.iris().encoder().extractAndEncode(sample2);
```
## Match
Operations for matching iris templates between each other.
### Match templates
```java
byte[] template1 = instance.iris().encoder().encode(template);
byte[] template2 = instance.iris().encoder().extractAndEncode(sample2);
boolean matches = instance.matcher().matches(template1, template2);
```

Communication
-------------
If you found a bug and can provide steps to reliably reproduce it, or if you
have a feature request, please
[open an issue](https://github.com/biometric-technologies/biometric-sdk/issues). Other
questions may be addressed to the
[Biometric Technologies project maintainers](mailto:info@iriscan.net).

License
-------
Biometric Converter is released under the GNU PUB3 License. See the
[LICENSE](https://github.com/biometric-technologies/biometric-converte/blob/master/LICENSE.md)
for details.


