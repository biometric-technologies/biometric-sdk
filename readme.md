# About
Biometric SDK is a complete open-source Software Development Kit for different biometric modalities.

# Structure

NOTE: Some modules are unavailable yet.

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


