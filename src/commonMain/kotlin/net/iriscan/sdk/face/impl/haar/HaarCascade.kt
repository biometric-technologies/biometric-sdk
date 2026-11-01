package net.iriscan.sdk.face.impl.haar

/**
 * A boosted cascade of Haar-like features, read from the XML files that OpenCV ships and trains
 * (`haarcascade_frontalface_alt.xml`, `haarcascade_eye.xml`, ...).
 *
 * ## What a cascade is
 *
 * The cascade looks at one small window of a grayscale image at a time ([windowWidth] x
 * [windowHeight] pixels, 20 x 20 for the faces and eyes used here) and answers "object or not".
 * It does so in [stages]. Every stage adds up the votes of a few weak classifiers and rejects the
 * window as soon as the sum is below the stage threshold, so most windows are thrown out by the
 * first two or three stages and only real faces pass all of them (22 for the face cascade).
 *
 * A weak classifier is a tiny decision tree, in practice a single node (a "stump"): it computes
 * one [Feature] in the window, compares it with a threshold and returns one of two leaf values.
 *
 * A Haar-like feature is a weighted sum of pixel sums over 2 or 3 rectangles, for example
 * "the band across the eyes is darker than the band below it": rectangle A with weight -1 plus
 * rectangle B (inside A) with weight +3. With an integral image every rectangle sum costs four
 * reads, which is why the whole detector is fast enough without any native library.
 *
 * ## File format
 *
 * Only the format OpenCV writes since version 2.4 is read ("opencv-cascade-classifier",
 * `stageType` BOOST, `featureType` HAAR). Its parts:
 *
 * ```
 * <cascade>
 *   <width>20</width> <height>20</height>          window size
 *   <stages>
 *     <_>                                          one stage
 *       <maxWeakCount>3</maxWeakCount>              number of weak classifiers in this stage
 *       <stageThreshold>0.82</stageThreshold>
 *       <weakClassifiers>
 *         <_>
 *           <internalNodes>0 -1 17 0.004</internalNodes>   per node: left right featureIndex threshold
 *           <leafValues>0.03 0.84</leafValues>
 *         </_> ...
 *   <features>
 *     <_> <rects> <_>3 7 14 4 -1.</_> <_>3 9 14 2 2.</_> </rects> </_>    x y width height weight
 * ```
 *
 * Node children follow OpenCV's convention: a value > 0 is the index of the next node in the same
 * tree, a value <= 0 is a leaf, `leafValues[-value]`. A stump is therefore "0 -1 feature threshold":
 * below the threshold leaf 0, otherwise leaf 1.
 *
 * Tilted (45 degree) features are not supported; none of the cascades the SDK uses has them, and
 * [parse] rejects a file that does.
 *
 * The parser is a small purpose-built reader for this one schema, not a general XML parser, so
 * that the class stays in common code without an XML dependency on every platform.
 */
internal class HaarCascade(
    val windowWidth: Int,
    val windowHeight: Int,
    val stages: List<Stage>,
    val features: List<Feature>,
) {
    /** The window passes the stage when the sum of its weak classifiers' votes is >= [threshold]. */
    class Stage(val threshold: Double, val classifiers: List<WeakClassifier>)

    /** A decision tree: [nodes] with the root at index 0, and the values of its leaves. */
    class WeakClassifier(val nodes: List<Node>, val leaves: DoubleArray)

    /**
     * Tree node: compare feature number [featureIndex] with [threshold]; go to [left] when the
     * feature value is below it and to [right] otherwise. Children <= 0 are leaves (see the class
     * documentation).
     */
    class Node(val left: Int, val right: Int, val featureIndex: Int, val threshold: Double)

    /** Weighted sum of the pixel sums over [rects], in window coordinates. */
    class Feature(val rects: List<WeightedRect>)

    class WeightedRect(val x: Int, val y: Int, val width: Int, val height: Int, val weight: Double)

    companion object {
        /** Numbers per tree node in `internalNodes`: left, right, feature index, threshold. */
        private const val NODE_FIELDS = 4

        /** Numbers per rectangle in `rects`: x, y, width, height, weight. */
        private const val RECT_FIELDS = 5

        private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        private val STAGE = Regex("<maxWeakCount>\\s*(\\d+)\\s*</maxWeakCount>\\s*<stageThreshold>([^<]+)</stageThreshold>")
        private val WEAK = Regex("<internalNodes>([^<]+)</internalNodes>\\s*<leafValues>([^<]+)</leafValues>")
        private val FEATURE = Regex("<rects>(.*?)</rects>(\\s*<tilted>\\s*(\\d)\\s*</tilted>)?", RegexOption.DOT_MATCHES_ALL)
        private val RECT = Regex("<_>([^<]+)</_>")

        /** Reads a cascade from the text of an OpenCV cascade XML file. */
        fun parse(xml: String): HaarCascade {
            val text = COMMENT.replace(xml, "")
            val cascade = section(text, "cascade")
            require(single(cascade, "stageType") == "BOOST") { "Only boosted cascades (stageType BOOST) are supported" }
            require(single(cascade, "featureType") == "HAAR") { "Only Haar feature cascades (featureType HAAR) are supported" }
            val width = single(cascade, "width").toInt()
            val height = single(cascade, "height").toInt()

            val stagesText = section(cascade, "stages")
            val stageHeads = STAGE.findAll(stagesText).toList()
            val weak = WEAK.findAll(stagesText).map { match ->
                val nodeNumbers = numbers(match.groupValues[1])
                require(nodeNumbers.size % NODE_FIELDS == 0) { "Malformed internalNodes: ${match.groupValues[1].trim()}" }
                val nodes = nodeNumbers.chunked(NODE_FIELDS).map {
                    Node(left = it[0].toInt(), right = it[1].toInt(), featureIndex = it[2].toInt(), threshold = it[3])
                }
                val leaves = numbers(match.groupValues[2]).toDoubleArray()
                require(leaves.size == nodes.size + 1) { "A tree with ${nodes.size} nodes needs ${nodes.size + 1} leaves" }
                WeakClassifier(nodes, leaves)
            }.toList()
            require(stageHeads.sumOf { it.groupValues[1].toInt() } == weak.size) {
                "The stages declare ${stageHeads.sumOf { it.groupValues[1].toInt() }} weak classifiers, the file has ${weak.size}"
            }
            var next = 0
            val stages = stageHeads.map { head ->
                val count = head.groupValues[1].toInt()
                Stage(head.groupValues[2].trim().toDouble(), weak.subList(next, next + count)).also { next += count }
            }

            val features = FEATURE.findAll(section(cascade, "features")).map { match ->
                require(match.groupValues[3] != "1") { "Tilted Haar features are not supported" }
                Feature(RECT.findAll(match.groupValues[1]).map { rect ->
                    val v = numbers(rect.groupValues[1])
                    require(v.size == RECT_FIELDS) { "Malformed rectangle: ${rect.groupValues[1].trim()}" }
                    WeightedRect(v[0].toInt(), v[1].toInt(), v[2].toInt(), v[3].toInt(), v[4])
                }.toList())
            }.toList()

            val maxFeature = stages.flatMap { s -> s.classifiers.flatMap { c -> c.nodes.map { it.featureIndex } } }.maxOrNull() ?: -1
            require(maxFeature < features.size) { "Feature $maxFeature is used but the file defines ${features.size}" }
            return HaarCascade(width, height, stages, features)
        }

        /** Text between the first `<tag ...>` and the last `</tag>`. */
        private fun section(text: String, tag: String): String {
            val open = Regex("<$tag(\\s[^>]*)?>").find(text) ?: throw IllegalArgumentException("No <$tag> in the cascade file")
            val end = text.lastIndexOf("</$tag>")
            require(end > open.range.last) { "No </$tag> in the cascade file" }
            return text.substring(open.range.last + 1, end)
        }

        /** Content of the first `<tag>value</tag>`. */
        private fun single(text: String, tag: String): String =
            Regex("<$tag>\\s*([^<]*?)\\s*</$tag>").find(text)?.groupValues?.get(1)
                ?: throw IllegalArgumentException("No <$tag> in the cascade file")

        private fun numbers(text: String): List<Double> =
            text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toDouble() }
    }
}
