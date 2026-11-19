// Ported from SourceAFIS for Java, Copyright Robert Važan, Apache License 2.0; see NOTICE.md.
package net.iriscan.sdk.finger.impl.afis

internal object MinutiaCollector {
    private fun collect(minutiae: MutableList<FeatureMinutia>, skeleton: Skeleton, type: MinutiaType) {
        for (sminutia in skeleton.minutiae)
            if (sminutia.ridges.size == 1)
                minutiae.add(FeatureMinutia(sminutia.position, sminutia.ridges[0].direction(), type))
    }

    fun collect(ridges: Skeleton, valleys: Skeleton): MutableList<FeatureMinutia> {
        val minutiae = ArrayList<FeatureMinutia>()
        collect(minutiae, ridges, MinutiaType.ENDING)
        collect(minutiae, valleys, MinutiaType.BIFURCATION)
        return minutiae
    }
}

internal object InnerMinutiaeFilter {
    fun apply(minutiae: MutableList<FeatureMinutia>, mask: BooleanMatrix) {
        minutiae.removeAll { minutia ->
            val arrow = (DoubleAngle.toVector(minutia.direction.toDouble()) * -Parameters.MASK_DISPLACEMENT).round()
            !mask.get(minutia.position + arrow, false)
        }
    }
}

internal object MinutiaCloudFilter {
    fun apply(minutiae: MutableList<FeatureMinutia>) {
        val radiusSq = sq(Parameters.MINUTIA_CLOUD_RADIUS)
        val removed = minutiae.filter { minutia ->
            Parameters.MAX_CLOUD_SIZE < minutiae.count { (it.position - minutia.position).lengthSq() <= radiusSq } - 1
        }
        for (minutia in removed) minutiae.removeRef(minutia)
    }
}

internal object TopMinutiaeFilter {
    fun apply(minutiae: MutableList<FeatureMinutia>): MutableList<FeatureMinutia> {
        if (minutiae.size <= Parameters.MAX_MINUTIAE) return minutiae
        val keys = HashMap<FeatureMinutia, Int>()
        for (minutia in minutiae) {
            val distances = minutiae.map { (minutia.position - it.position).lengthSq() }.sorted()
            keys[minutia] = if (distances.size > Parameters.SORT_BY_NEIGHBOR) distances[Parameters.SORT_BY_NEIGHBOR] else Int.MAX_VALUE
        }
        return minutiae.sortedByDescending { keys.getValue(it) }.take(Parameters.MAX_MINUTIAE).toMutableList()
    }
}

/** Minutiae in matching order with their neighbor edges; SourceAFIS SearchTemplate. */
internal class SearchTemplate(features: FeatureTemplate) {
    val width: Short = features.size.x.toShort()
    val height: Short = features.size.y.toShort()
    val minutiae: Array<SearchMinutia> = features.minutiae.map { SearchMinutia(it) }
        .sortedWith(
            compareBy<SearchMinutia> { ((it.x * PRIME) + it.y) * PRIME }
                .thenBy { it.x }
                .thenBy { it.y }
                .thenBy { it.direction.toDouble() }
                .thenBy { it.type }
        ).toTypedArray()
    val edges: Array<Array<NeighborEdge>> = NeighborEdge.buildTable(minutiae)

    fun features() = FeatureTemplate(IntPoint(width.toInt(), height.toInt()), minutiae.map { it.feature() }.toMutableList())

    companion object {
        private const val PRIME = 1610612741
    }
}
