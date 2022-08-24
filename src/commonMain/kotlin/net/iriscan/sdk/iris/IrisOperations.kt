package net.iriscan.sdk.iris

/**
 * @author Slava Gornostal
 *
 * Iris SDK operations
 *
 * @see IrisExtractor
 * @see IrisEncoder
 * @see IrisMatcher
 */
interface IrisOperations {
    fun extractor(): IrisExtractor
    fun encoder(): IrisEncoder
    fun matcher(): IrisMatcher
}