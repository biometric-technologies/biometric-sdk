// Ported from SourceAFIS for Java, Copyright Robert Važan, Apache License 2.0; see NOTICE.md.
package net.iriscan.sdk.finger.impl.afis

import kotlin.math.PI

/** The tuned constants of SourceAFIS, unchanged. */
internal object Parameters {
    const val BLOCK_SIZE = 15
    const val HISTOGRAM_DEPTH = 256
    const val CLIPPED_CONTRAST = 0.08
    const val MIN_ABSOLUTE_CONTRAST = 17 / 255.0
    const val MIN_RELATIVE_CONTRAST = 0.34
    const val RELATIVE_CONTRAST_SAMPLE = 168568
    const val RELATIVE_CONTRAST_PERCENTILE = 0.49
    const val MASK_VOTE_RADIUS = 7
    const val MASK_VOTE_MAJORITY = 0.51
    const val MASK_VOTE_BORDER_DISTANCE = 4
    const val BLOCK_ERRORS_VOTE_RADIUS = 1
    const val BLOCK_ERRORS_VOTE_MAJORITY = 0.7
    const val BLOCK_ERRORS_VOTE_BORDER_DISTANCE = 4
    const val MAX_EQUALIZATION_SCALING = 3.99
    const val MIN_EQUALIZATION_SCALING = 0.25
    const val MIN_ORIENTATION_RADIUS = 2.0
    const val MAX_ORIENTATION_RADIUS = 6.0
    const val ORIENTATION_SPLIT = 50
    const val ORIENTATIONS_CHECKED = 20
    const val ORIENTATION_SMOOTHING_RADIUS = 1
    const val PARALLEL_SMOOTHING_RESOLUTION = 32
    const val PARALLEL_SMOOTHING_RADIUS = 7
    const val PARALLEL_SMOOTHING_STEP = 1.59
    const val ORTHOGONAL_SMOOTHING_RESOLUTION = 11
    const val ORTHOGONAL_SMOOTHING_RADIUS = 4
    const val ORTHOGONAL_SMOOTHING_STEP = 1.11
    const val BINARIZED_VOTE_RADIUS = 2
    const val BINARIZED_VOTE_MAJORITY = 0.61
    const val BINARIZED_VOTE_BORDER_DISTANCE = 17
    const val INNER_MASK_BORDER_DISTANCE = 14
    const val MASK_DISPLACEMENT = 10.06
    const val MINUTIA_CLOUD_RADIUS = 20
    const val MAX_CLOUD_SIZE = 4
    const val MAX_MINUTIAE = 100
    const val SORT_BY_NEIGHBOR = 5
    const val EDGE_TABLE_NEIGHBORS = 9
    const val THINNING_ITERATIONS = 26
    const val MAX_PORE_ARM = 41
    const val SHORTEST_JOINED_ENDING = 7
    const val MAX_RUPTURE_SIZE = 5
    const val MAX_GAP_SIZE = 20
    const val GAP_ANGLE_OFFSET = 22
    const val TOLERATED_GAP_OVERLAP = 2
    const val MIN_TAIL_LENGTH = 21
    const val MIN_FRAGMENT_LENGTH = 22
    const val MAX_DISTANCE_ERROR = 13
    val MAX_ANGLE_ERROR: Float = FloatAngle.PI / 180 * 10
    const val MAX_GAP_ANGLE = 45 * PI / 180 // Math.toRadians(45)
    const val RIDGE_DIRECTION_SAMPLE = 21
    const val RIDGE_DIRECTION_SKIP = 1
    const val MAX_TRIED_ROOTS = 70
    const val MIN_ROOT_EDGE_LENGTH = 58
    const val MAX_ROOT_EDGE_LOOKUPS = 1633
    const val MIN_SUPPORTING_EDGES = 1
    const val DISTANCE_ERROR_FLATNESS = 0.69
    const val ANGLE_ERROR_FLATNESS = 0.27
    const val MINUTIA_SCORE = 0.032
    const val MINUTIA_FRACTION_SCORE = 8.98
    const val MINUTIA_TYPE_SCORE = 0.629
    const val SUPPORTED_MINUTIA_SCORE = 0.193
    const val EDGE_SCORE = 0.265
    const val DISTANCE_ACCURACY_SCORE = 9.9
    const val ANGLE_ACCURACY_SCORE = 2.79
    const val THRESHOLD_FMR_MAX = 8.48
    const val THRESHOLD_FMR_2 = 11.12
    const val THRESHOLD_FMR_10 = 14.15
    const val THRESHOLD_FMR_100 = 18.22
    const val THRESHOLD_FMR_1000 = 22.39
    const val THRESHOLD_FMR_10_000 = 27.24
    const val THRESHOLD_FMR_100_000 = 32.01
}
