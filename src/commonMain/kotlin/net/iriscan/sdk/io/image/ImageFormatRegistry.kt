package net.iriscan.sdk.io.image

/**
 * @author Slava Gornostal
 *
 * Image formats registry
 */
internal class ImageFormatRegistry {
    companion object {
        private val SERIALIZERS = mutableListOf<ImageSerializer>()
    }

    fun register(serializer: ImageSerializer) =
        SERIALIZERS.add(serializer)

    fun registerFirst(serializer: ImageSerializer) =
        SERIALIZERS.add(0, serializer)
}