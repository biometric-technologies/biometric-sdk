package net.iriscan.sdk.iris

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.iris.impl.IrisProbe
import net.iriscan.sdk.iris.impl.IrisTemplate
import net.iriscan.sdk.iris.impl.compare
import net.iriscan.sdk.iris.impl.encodeNormalized
import net.iriscan.sdk.iris.impl.extractNormalized
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Accuracy benchmark on a real iris dataset. Skipped unless IRIS_DATASET is set.
 *
 * IRIS_DATASET   directory with eye images (bmp, jpg, png, tif), searched recursively
 * IRIS_SUBJECT   regex with one group that extracts the identity (one eye of one person)
 *                from the path relative to IRIS_DATASET. Default: the parent directory,
 *                which fits layouts such as CASIA-Iris-Interval "001/L/S1001L01.jpg".
 * IRIS_LIMIT     optional maximum number of images
 * IRIS_REPORT    optional directory for scores.csv and det.csv
 *
 * Example:
 *   IRIS_DATASET=/data/CASIA-Iris-Interval ./gradlew jvmTest --tests '*IrisDatasetBenchmark*'
 *
 * Reports failure to enroll, genuine and impostor score distributions, EER and
 * FNMR at fixed FMR, in the terms of ISO/IEC 19795-1. Use the result to choose
 * IrisMatchProperties.threshold for your sensors and population.
 */
class IrisDatasetBenchmark {

    @Test
    fun benchmark() {
        val root = System.getenv("IRIS_DATASET")?.let { File(it) } ?: run {
            println("IrisDatasetBenchmark skipped: set IRIS_DATASET")
            return
        }
        val subjectRegex = System.getenv("IRIS_SUBJECT")?.let { Regex(it) }
        val limit = System.getenv("IRIS_LIMIT")?.toInt() ?: Int.MAX_VALUE
        val extract = IrisExtractProperties()
        val encode = IrisEncodeProperties()
        val match = IrisMatchProperties()

        val files = root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("bmp", "jpg", "jpeg", "png", "tif", "tiff") }
            .sortedBy { it.path }
            .take(limit)
            .toList()
        require(files.isNotEmpty()) { "No images found in $root" }

        val templates = ArrayList<Pair<String, IrisTemplate>>()
        var failures = 0
        val started = System.nanoTime()
        for (f in files) {
            val rel = f.relativeTo(root).path
            val subject = subjectRegex?.find(rel)?.groupValues?.get(1) ?: (f.parentFile.relativeTo(root).path)
            val image = readGray(f)
            val normalized = image?.let { extractNormalized(it, extract) }
            if (normalized == null) {
                failures++
                continue
            }
            templates += subject to encodeNormalized(normalized, encode)
        }
        val msPerImage = (System.nanoTime() - started) / 1e6 / files.size

        val genuine = ArrayList<Double>()
        val impostor = ArrayList<Double>()
        val report = System.getenv("IRIS_REPORT")?.let { File(it).apply { mkdirs() } }
        val scoresOut = report?.let { File(it, "scores.csv").printWriter() }
        scoresOut?.println("subject_a,subject_b,genuine,score,compared_bits,shift")
        for (i in templates.indices) {
            val probe = IrisProbe(templates[i].second, match)
            for (j in i + 1 until templates.size) {
                val r = compare(probe, templates[j].second, match)
                val same = templates[i].first == templates[j].first
                if (same) genuine += r.score else impostor += r.score
                scoresOut?.println("${templates[i].first},${templates[j].first},$same,${r.score},${r.comparedBits},${r.shift}")
            }
        }
        scoresOut?.close()

        println("Images: ${files.size}, failure to enroll: $failures (${pct(failures.toDouble() / files.size)}), ${"%.0f".format(msPerImage)} ms/image")
        println("Identities: ${templates.map { it.first }.distinct().size}, genuine pairs: ${genuine.size}, impostor pairs: ${impostor.size}")
        if (genuine.isEmpty() || impostor.isEmpty()) return
        println("Genuine score:  ${summary(genuine)}")
        println("Impostor score: ${summary(impostor)}")
        val g = genuine.sorted()
        val im = impostor.sorted()
        val (eer, eerThreshold) = eer(g, im)
        println("EER: ${pct(eer)} at threshold ${"%.3f".format(eerThreshold)}")
        for (fmr in listOf(1e-2, 1e-3, 1e-4, 1e-5)) {
            if (im.size * fmr < 1) break
            val t = thresholdAtFmr(im, fmr)
            println("FMR ${fmr}: threshold ${"%.3f".format(t)}, FNMR ${pct(fnmr(g, t))}")
        }
        val t = match.threshold
        println("Default threshold $t: FNMR ${pct(fnmr(g, t))}, FMR ${pct(fmr(im, t))}")
        report?.let { dir ->
            File(dir, "det.csv").printWriter().use { out ->
                out.println("threshold,fmr,fnmr")
                var th = 0.40
                while (th <= 1.0001) {
                    out.println("${"%.3f".format(th)},${fmr(im, th)},${fnmr(g, th)}")
                    th += 0.005
                }
            }
            println("Scores and DET points written to $dir")
        }
    }

    private fun readGray(f: File): Image? {
        val img = ImageIO.read(f) ?: return null
        val w = img.width
        val h = img.height
        val colors = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val rgb = img.getRGB(x, y)
            val r = (rgb shr 16) and 0xFF
            val gg = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            colors[y * w + x] = (0.299 * r + 0.587 * gg + 0.114 * b).toInt()
        }
        return Image(w, h, ImageColorType.GRAY, colors)
    }

    private fun fnmr(sortedGenuine: List<Double>, t: Double) = sortedGenuine.count { it < t }.toDouble() / sortedGenuine.size
    private fun fmr(sortedImpostor: List<Double>, t: Double) = sortedImpostor.count { it >= t }.toDouble() / sortedImpostor.size

    /** Lowest threshold at which the false match rate does not exceed [target]. */
    private fun thresholdAtFmr(sortedImpostor: List<Double>, target: Double): Double {
        val allowed = (target * sortedImpostor.size).toInt()
        val idx = sortedImpostor.size - 1 - allowed
        return if (idx < 0) 0.0 else sortedImpostor[idx] + 1e-9
    }

    private fun eer(g: List<Double>, im: List<Double>): Pair<Double, Double> {
        var best = 1.0
        var bestT = 0.0
        var th = 0.40
        while (th <= 1.0) {
            val e = maxOf(fnmr(g, th), fmr(im, th))
            if (e < best) {
                best = e; bestT = th
            }
            th += 0.001
        }
        return best to bestT
    }

    private fun summary(v: List<Double>): String {
        val mean = v.average()
        val sd = kotlin.math.sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size)
        return "mean ${"%.3f".format(mean)}, sd ${"%.3f".format(sd)}, min ${"%.3f".format(v.min())}, max ${"%.3f".format(v.max())}"
    }

    private fun pct(v: Double) = "%.3f%%".format(v * 100)
}
