// Ported from SourceAFIS for Java, Copyright Robert Važan, Apache License 2.0; see NOTICE.md.
package net.iriscan.sdk.finger.impl.afis

/** Binary image to a traced and cleaned skeleton graph. */
internal object Skeletons {
    fun create(binary: BooleanMatrix, type: SkeletonType): Skeleton {
        val thinned = BinaryThinning.thin(binary)
        val skeleton = SkeletonTracing.trace(thinned, type)
        SkeletonFilters.apply(skeleton)
        return skeleton
    }
}

internal object BinaryThinning {
    private const val SKELETON = 0
    private const val ENDING = 1
    private const val REMOVABLE = 2

    private val neighborhoodTypes = IntArray(256) { mask ->
        val tl = mask and 1 != 0
        val tc = mask and 2 != 0
        val tr = mask and 4 != 0
        val cl = mask and 8 != 0
        val cr = mask and 16 != 0
        val bl = mask and 32 != 0
        val bc = mask and 64 != 0
        val br = mask and 128 != 0
        val count = mask.countOneBits()
        val diagonal = !tc && !cl && tl || !cl && !bc && bl || !bc && !cr && br || !cr && !tc && tr
        val horizontal = !tc && !bc && (tr || cr || br) && (tl || cl || bl)
        val vertical = !cl && !cr && (tl || tc || tr) && (bl || bc || br)
        when {
            count == 1 -> ENDING
            !diagonal && !horizontal && !vertical -> REMOVABLE
            else -> SKELETON
        }
    }

    private fun isFalseEnding(binary: BooleanMatrix, ending: IntPoint): Boolean {
        for (relativeNeighbor in IntPoint.CORNER_NEIGHBORS) {
            val neighbor = ending + relativeNeighbor
            if (binary[neighbor]) {
                var count = 0
                for (relative2 in IntPoint.CORNER_NEIGHBORS) if (binary.get(neighbor + relative2, false)) ++count
                return count > 2
            }
        }
        return false
    }

    fun thin(input: BooleanMatrix): BooleanMatrix {
        val size = input.size()
        val partial = BooleanMatrix(size)
        for (y in 1 until size.y - 1) for (x in 1 until size.x - 1) partial[x, y] = input[x, y]
        val thinned = BooleanMatrix(size)
        var removedAnything = true
        var i = 0
        while (i < Parameters.THINNING_ITERATIONS && removedAnything) {
            removedAnything = false
            for (evenY in 0 until 2) for (evenX in 0 until 2) {
                var y = 1 + evenY
                while (y < size.y - 1) {
                    var x = 1 + evenX
                    while (x < size.x - 1) {
                        if (partial[x, y] && !thinned[x, y] &&
                            !(partial[x, y - 1] && partial[x, y + 1] && partial[x - 1, y] && partial[x + 1, y])
                        ) {
                            val neighbors = (if (partial[x + 1, y + 1]) 128 else 0) or
                                (if (partial[x, y + 1]) 64 else 0) or
                                (if (partial[x - 1, y + 1]) 32 else 0) or
                                (if (partial[x + 1, y]) 16 else 0) or
                                (if (partial[x - 1, y]) 8 else 0) or
                                (if (partial[x + 1, y - 1]) 4 else 0) or
                                (if (partial[x, y - 1]) 2 else 0) or
                                (if (partial[x - 1, y - 1]) 1 else 0)
                            val kind = neighborhoodTypes[neighbors]
                            if (kind == REMOVABLE || kind == ENDING && isFalseEnding(partial, IntPoint(x, y))) {
                                removedAnything = true
                                partial[x, y] = false
                            } else thinned[x, y] = true
                        }
                        x += 2
                    }
                    y += 2
                }
            }
            ++i
        }
        return thinned
    }
}

internal object SkeletonTracing {
    private fun findMinutiae(thinned: BooleanMatrix): List<IntPoint> {
        val result = ArrayList<IntPoint>()
        thinned.size().forEach { at ->
            if (thinned[at]) {
                var count = 0
                for (relative in IntPoint.CORNER_NEIGHBORS) if (thinned.get(at + relative, false)) ++count
                if (count == 1 || count > 2) result.add(at)
            }
        }
        return result
    }

    private fun linkNeighboringMinutiae(minutiae: List<IntPoint>): MutableMap<IntPoint, MutableList<IntPoint>> {
        val linking = HashMap<IntPoint, MutableList<IntPoint>>()
        for (minutiaPos in minutiae) {
            var ownLinks: MutableList<IntPoint>? = null
            for (neighborRelative in IntPoint.CORNER_NEIGHBORS) {
                val neighborPos = minutiaPos + neighborRelative
                val neighborLinks = linking[neighborPos] ?: continue
                if (neighborLinks !== ownLinks) {
                    if (ownLinks != null) {
                        neighborLinks.addAll(ownLinks)
                        for (mergedPos in ownLinks) linking[mergedPos] = neighborLinks
                    }
                    ownLinks = neighborLinks
                }
            }
            if (ownLinks == null) ownLinks = ArrayList()
            ownLinks.add(minutiaPos)
            linking[minutiaPos] = ownLinks
        }
        return linking
    }

    private fun minutiaCenters(skeleton: Skeleton, linking: Map<IntPoint, List<IntPoint>>): Map<IntPoint, SkeletonMinutia> {
        val centers = HashMap<IntPoint, SkeletonMinutia>()
        for (currentPos in linking.keys.sorted()) {
            val linked = linking.getValue(currentPos)
            val primaryPos = linked[0]
            if (!centers.containsKey(primaryPos)) {
                var sumX = 0
                var sumY = 0
                for (p in linked) {
                    sumX += p.x
                    sumY += p.y
                }
                val minutia = SkeletonMinutia(IntPoint(sumX / linked.size, sumY / linked.size))
                skeleton.addMinutia(minutia)
                centers[primaryPos] = minutia
            }
            centers[currentPos] = centers.getValue(primaryPos)
        }
        return centers
    }

    private fun traceRidges(thinned: BooleanMatrix, minutiaePoints: Map<IntPoint, SkeletonMinutia>) {
        val leads = HashMap<IntPoint, SkeletonRidge>()
        for (minutiaPoint in minutiaePoints.keys.sorted()) {
            for (startRelative in IntPoint.CORNER_NEIGHBORS) {
                val start = minutiaPoint + startRelative
                if (thinned.get(start, false) && !minutiaePoints.containsKey(start) && !leads.containsKey(start)) {
                    val ridge = SkeletonRidge()
                    ridge.points.add(minutiaPoint)
                    ridge.points.add(start)
                    var previous = minutiaPoint
                    var current = start
                    do {
                        var next = IntPoint.ZERO
                        for (nextRelative in IntPoint.CORNER_NEIGHBORS) {
                            next = current + nextRelative
                            if (thinned.get(next, false) && next != previous) break
                        }
                        previous = current
                        current = next
                        ridge.points.add(current)
                    } while (!minutiaePoints.containsKey(current))
                    ridge.start(minutiaePoints.getValue(minutiaPoint))
                    ridge.end(minutiaePoints.getValue(current))
                    leads[ridge.points[1]] = ridge
                    leads[ridge.reversed.points[1]] = ridge
                }
            }
        }
    }

    private fun fixLinkingGaps(skeleton: Skeleton) {
        for (minutia in skeleton.minutiae)
            for (ridge in minutia.ridges)
                if (ridge.points[0] != minutia.position) {
                    val filling = ridge.points[0].lineTo(minutia.position)
                    for (i in 1 until filling.size) ridge.reversed.points.add(filling[i])
                }
    }

    fun trace(thinned: BooleanMatrix, type: SkeletonType): Skeleton {
        val skeleton = Skeleton(type, thinned.size())
        val linking = linkNeighboringMinutiae(findMinutiae(thinned))
        traceRidges(thinned, minutiaCenters(skeleton, linking))
        fixLinkingGaps(skeleton)
        return skeleton
    }
}

internal object SkeletonFilters {
    fun apply(skeleton: Skeleton) {
        removeDots(skeleton)
        removePores(skeleton)
        removeGaps(skeleton)
        removeTails(skeleton)
        removeFragments(skeleton)
    }

    private fun removeDots(skeleton: Skeleton) {
        val removed = skeleton.minutiae.filter { it.ridges.isEmpty() }
        for (minutia in removed) skeleton.removeMinutia(minutia)
    }

    private fun removeKnots(skeleton: Skeleton) {
        for (minutia in skeleton.minutiae) {
            if (minutia.ridges.size == 2 && minutia.ridges[0].reversed !== minutia.ridges[1]) {
                var extended = minutia.ridges[0].reversed
                var removed = minutia.ridges[1]
                if (extended.points.size < removed.points.size) {
                    val tmp = extended
                    extended = removed.reversed
                    removed = tmp.reversed
                }
                extended.points.removeLast()
                for (point in removed.points) extended.points.add(point)
                extended.end(removed.end())
                removed.detach()
            }
        }
        removeDots(skeleton)
    }

    private fun removePores(skeleton: Skeleton) {
        for (minutia in skeleton.minutiae) {
            if (minutia.ridges.size == 3) {
                for (exit in 0 until 3) {
                    val exitRidge = minutia.ridges[exit]
                    val arm1 = minutia.ridges[(exit + 1) % 3]
                    val arm2 = minutia.ridges[(exit + 2) % 3]
                    if (arm1.end() === arm2.end() && exitRidge.end() !== arm1.end() && arm1.end() !== minutia && exitRidge.end() !== minutia) {
                        val end = arm1.end()!!
                        if (end.ridges.size == 3 && arm1.points.size <= Parameters.MAX_PORE_ARM && arm2.points.size <= Parameters.MAX_PORE_ARM) {
                            arm1.detach()
                            arm2.detach()
                            val merged = SkeletonRidge()
                            merged.start(minutia)
                            merged.end(end)
                            for (point in minutia.position.lineTo(end.position)) merged.points.add(point)
                        }
                        break
                    }
                }
            }
        }
        removeKnots(skeleton)
    }

    private class Gap(val distance: Int, val end1: SkeletonMinutia, val end2: SkeletonMinutia)

    private val gapOrder = Comparator<Gap> { a, b ->
        val d = a.distance.compareTo(b.distance)
        if (d != 0) return@Comparator d
        val e1 = a.end1.position.compareTo(b.end1.position)
        if (e1 != 0) e1 else a.end2.position.compareTo(b.end2.position)
    }

    private fun angleSampleForGapRemoval(minutia: SkeletonMinutia): IntPoint {
        val ridge = minutia.ridges[0]
        return if (Parameters.GAP_ANGLE_OFFSET < ridge.points.size) ridge.points[Parameters.GAP_ANGLE_OFFSET]
        else ridge.end()!!.position
    }

    private fun isWithinGapLimits(end1: SkeletonMinutia, end2: SkeletonMinutia): Boolean {
        val distanceSq = (end1.position - end2.position).lengthSq()
        if (distanceSq <= sq(Parameters.MAX_RUPTURE_SIZE)) return true
        if (distanceSq > sq(Parameters.MAX_GAP_SIZE)) return false
        val gapDirection = DoubleAngle.atan(end1.position, end2.position)
        val direction1 = DoubleAngle.atan(end1.position, angleSampleForGapRemoval(end1))
        if (DoubleAngle.distance(direction1, DoubleAngle.opposite(gapDirection)) > Parameters.MAX_GAP_ANGLE) return false
        val direction2 = DoubleAngle.atan(end2.position, angleSampleForGapRemoval(end2))
        return DoubleAngle.distance(direction2, gapDirection) <= Parameters.MAX_GAP_ANGLE
    }

    private fun isRidgeOverlapping(line: Array<IntPoint>, shadow: BooleanMatrix): Boolean {
        for (i in Parameters.TOLERATED_GAP_OVERLAP until line.size - Parameters.TOLERATED_GAP_OVERLAP)
            if (shadow[line[i]]) return true
        return false
    }

    private fun removeGaps(skeleton: Skeleton) {
        val queue = JavaPriorityQueue(gapOrder)
        for (end1 in skeleton.minutiae)
            if (end1.ridges.size == 1 && end1.ridges[0].points.size >= Parameters.SHORTEST_JOINED_ENDING)
                for (end2 in skeleton.minutiae)
                    if (end2 !== end1 && end2.ridges.size == 1 && end1.ridges[0].end() !== end2 &&
                        end2.ridges[0].points.size >= Parameters.SHORTEST_JOINED_ENDING && isWithinGapLimits(end1, end2)
                    ) queue.add(Gap((end1.position - end2.position).lengthSq(), end1, end2))
        val shadow = skeleton.shadow()
        while (!queue.isEmpty()) {
            val gap = queue.remove()
            if (gap.end1.ridges.size == 1 && gap.end2.ridges.size == 1) {
                val line = gap.end1.position.lineTo(gap.end2.position)
                if (!isRidgeOverlapping(line, shadow)) {
                    val ridge = SkeletonRidge()
                    for (point in line) ridge.points.add(point)
                    ridge.start(gap.end1)
                    ridge.end(gap.end2)
                    for (point in line) shadow[point] = true
                }
            }
        }
        removeKnots(skeleton)
    }

    private fun removeTails(skeleton: Skeleton) {
        for (minutia in skeleton.minutiae)
            if (minutia.ridges.size == 1 && minutia.ridges[0].end()!!.ridges.size >= 3 &&
                minutia.ridges[0].points.size < Parameters.MIN_TAIL_LENGTH
            ) minutia.ridges[0].detach()
        removeDots(skeleton)
        removeKnots(skeleton)
    }

    private fun removeFragments(skeleton: Skeleton) {
        for (minutia in skeleton.minutiae)
            if (minutia.ridges.size == 1) {
                val ridge = minutia.ridges[0]
                if (ridge.end()!!.ridges.size == 1 && ridge.points.size < Parameters.MIN_FRAGMENT_LENGTH) ridge.detach()
            }
        removeDots(skeleton)
    }
}
