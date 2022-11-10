package net.iriscan.sdk.iris.impl

import net.iriscan.sdk.core.exception.BiometricNotFoundException
import net.iriscan.sdk.core.image.Circle
import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.core.image.Point
import net.iriscan.sdk.core.utils.*
import net.iriscan.sdk.iris.IrisExtractProperties
import kotlin.math.*

/**
 * @author Slava Gornostal
 */
private typealias CircleIntensity = Pair<Circle, Int>

internal fun extractInternal(sample: Image, props: IrisExtractProperties): Image {
    val original = sample.clone()
    grayscaleImg(sample)
    fillImg(sample, threshold = 200, kernel = 12, fillKernel = 4)
    gaussianFilterImg(sample)
    val circleModels = createCircleModels(min(sample.width, sample.height))
    val intenseRegions = getMostIntenseRegions(sample, props)
    val minPupilRadius = props.minPupilRadius ?: (0.05 * min(sample.width, sample.height)).toInt()
    val approximateMaxPupilRadius = props.maxPupilRadius ?: (5 * minPupilRadius)
    val possibleIntensities = mutableListOf<CircleIntensity>()
    for (region in intenseRegions) {
        // limit center to inside box
        val maxOffset = (region.r * 0.7071).toInt()
        for (cx in region.x - maxOffset..region.x + maxOffset step props.centerOffsetStep) {
            for (cy in region.y - maxOffset..region.y + maxOffset step props.centerOffsetStep) {
                val maxPupilRadius = min(
                    approximateMaxPupilRadius,
                    min(min(cx, cy), min(sample.width - cx, sample.height - cy))
                ) - props.pupilRadiusCalculationSteps
                val operatorRes = getMostIntenseCircle(
                    sample,
                    cx,
                    cy,
                    minPupilRadius,
                    maxPupilRadius,
                    props.pupilRadiusStep,
                    props.pupilRadiusCalculationSteps,
                    circleModels,
                    props.pupilAnglesToSearch,
                ) ?: continue
                possibleIntensities += operatorRes
            }
        }
    }
    val pupil = possibleIntensities.maxByOrNull { it.second }?.first
        ?: throw BiometricNotFoundException("Could not find pupil")
    val approximateMinIrisRadius = props.minIrisRadius ?: (1.3 * pupil.r).toInt()
    val approximateMaxIrisRadius = props.maxIrisRadius ?: (10 * pupil.r)
    val maxIirisRadius = min(
        approximateMaxIrisRadius,
        min(min(pupil.x, pupil.y), min(sample.width - pupil.x, sample.height - pupil.y))
    ) - props.irisRadiusCalculationSteps
    val iris = getMostIntenseCircle(
        sample,
        pupil.x,
        pupil.y,
        approximateMinIrisRadius,
        maxIirisRadius,
        props.irisRadiusStep,
        props.irisRadiusCalculationSteps,
        circleModels,
        props.irisAnglesToSearch
    )?.first ?: throw BiometricNotFoundException("Could not find pupil")
    return cutTextureFromSample(original, iris, pupil)
}

private fun cutTextureFromSample(
    sample: Image,
    iris: Circle,
    pupil: Circle,
): Image {
    val image = sample.clone()
    normalizeHistogramImg(image)
    val width = (2 * Math.PI * iris.r).toInt()
    val height = (iris.r - pupil.r)
    val result = createImg(width, height, ImageColorType.GRAY) { _, _ -> 255 }
    val angleStep = 2.0 * Math.PI / width
    for (x in 0 until width) {
        for (y in 0 until height) {
            val r = pupil.r + y
            val angle = x * angleStep
            val xr = pupil.x + round(cos(angle) * r).toInt()
            val yr = pupil.y + round(sin(angle) * r).toInt()
            result[x, y] = image[xr, yr]
        }
    }
    return result
}

private fun createCircleModels(size: Int): Array<out Iterable<Point>> {
    val circleModels = Array(size) { mutableSetOf<Point>() }
    for (x in -size..size) {
        for (y in -size..size) {
            val rt = sqrt((x * x).toDouble() + (y * y).toDouble())
            for (r in rt.toInt()..round(rt).toInt()) {
                if (r < size) {
                    circleModels[r].add(Point(x, y))
                }
            }
        }
    }
    return circleModels
}

private fun getMostIntenseCircle(
    image: Image,
    centerX: Int,
    centerY: Int,
    radiusMin: Int,
    radiusMax: Int,
    radiusStep: Int,
    calculationSteps: Int,
    circleModels: Array<out Iterable<Point>>,
    circleAngles: Array<out ClosedRange<Double>>
): CircleIntensity? {
    val radiusList = (radiusMin until radiusMax step radiusStep)
        .toList()
    val intensities = IntArray(radiusList.size)
    val anglesSum = circleAngles.sumOf { it.endInclusive - it.start }
    for ((radiusIndex, radius) in radiusList.withIndex()) {
        circleModels.slice(radius..radius + calculationSteps)
            .flatten()
            .filter {
                var angle = Math.toDegrees(atan2(it.y.toDouble(), it.x.toDouble()) + Math.PI / 2)
                if (angle < 0) {
                    angle += 360
                }
                circleAngles.any { an -> an.contains(angle) }
            }
            .forEach {
                intensities[radiusIndex] += (255 - image[centerX + it.x, centerY + it.y])
            }
        val divisor = ((anglesSum / 360.0) * 2 * Math.PI * radius).toInt() * calculationSteps
        if (divisor != 0) {
            intensities[radiusIndex] = intensities[radiusIndex] / divisor
        }
    }
    val intensityDiff =
        intensities[1 until intensities.size] - intensities[0 until intensities.size - 1]
    val maxIntensityIdx = intensityDiff.indices.maxByOrNull { intensityDiff[it] } ?: return null
    val radius = radiusMin + (maxIntensityIdx * radiusStep)
    return Circle(centerX, centerY, radius) to intensityDiff[maxIntensityIdx]
}

private fun getMostIntenseRegions(sample: Image, props: IrisExtractProperties): List<Circle> {
    val image = sample.clone()
    normalizeHistogramImg(image, null)
    var verticalProjection = IntArray(image.width)
    var horizontalProjection = IntArray(image.height)
    // skip 1/5 edge
    val verticalOffStart = props.verticalOffsetStart ?: (image.width / 5)
    val verticalOffEnd = props.verticalOffsetEnd ?: (image.width / 5)
    val horizontalOffStart = props.horizontalOffsetStart ?: (image.height / 5)
    val horizontalOffEnd = props.horizontalOffsetEnd ?: (image.height / 5)
    for (x in verticalOffStart until image.width - verticalOffEnd) {
        for (y in horizontalOffStart until image.height - horizontalOffEnd) {
            if (image[x, y] <= 64) {
                verticalProjection[x] += 1
                horizontalProjection[y] += 1
            }
        }
    }
    verticalProjection = verticalProjection.smooth(4)
    horizontalProjection = horizontalProjection.smooth(4)
    val verticalDrop = verticalProjection.sliceArray(verticalOffStart until image.width - verticalOffEnd)
        .filter { it > 0 }
        .toIntArray()
        .median()
    val horizontalDrop = horizontalProjection.sliceArray(horizontalOffStart until image.height - horizontalOffEnd)
        .filter { it > 0 }
        .toIntArray()
        .median()
    val regions = mutableListOf<Circle>()
    val vSegments = verticalProjection.findSegmentsGte(verticalDrop)
    val hSegments = horizontalProjection.findSegmentsGte(horizontalDrop)
    for (vs in vSegments) {
        for (hs in hSegments) {
            val x = (vs.first + vs.last) / 2
            val y = (hs.first + hs.last) / 2
            val r = min(vs.count(), hs.count())
            regions += Circle(x, y, r)
        }
    }
    return regions
}