package net.iriscan.sdk.face.haar

import net.iriscan.sdk.face.impl.haar.HaarCascade
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HaarCascadeTest {

    @Test
    fun `reads window size, stages, trees and features`() {
        val cascade = HaarCascade.parse(TWO_STAGE_CASCADE)

        assertEquals(20, cascade.windowWidth)
        assertEquals(20, cascade.windowHeight)
        assertEquals(2, cascade.stages.size)
        assertEquals(0.5, cascade.stages[0].threshold)
        assertEquals(1, cascade.stages[0].classifiers.size)
        assertEquals(2, cascade.stages[1].classifiers.size)

        val stump = cascade.stages[0].classifiers[0]
        assertEquals(1, stump.nodes.size)
        assertEquals(0, stump.nodes[0].left)
        assertEquals(-1, stump.nodes[0].right)
        assertEquals(1, stump.nodes[0].featureIndex)
        assertEquals(0.004, stump.nodes[0].threshold)
        assertContentEquals(doubleArrayOf(0.1, 0.9), stump.leaves)

        // a tree with two nodes: the root sends values below 0.1 to node 1, the rest to leaf 0
        val tree = cascade.stages[1].classifiers[1]
        assertEquals(2, tree.nodes.size)
        assertEquals(1, tree.nodes[0].left)
        assertEquals(0, tree.nodes[0].right)
        assertEquals(3, tree.leaves.size)

        assertEquals(2, cascade.features.size)
        val rects = cascade.features[1].rects
        assertEquals(3, rects.size)
        assertEquals(listOf(6, 4, 12, 9, -1.0), rects[0].let { listOf(it.x, it.y, it.width, it.height, it.weight) })
        assertEquals(3.0, rects[1].weight)
    }

    @Test
    fun `comments with tags in them are ignored`() {
        val cascade = HaarCascade.parse("<!-- <stages> <width>99</width> -->\n$TWO_STAGE_CASCADE")
        assertEquals(20, cascade.windowWidth)
    }

    @Test
    fun `tilted features are rejected`() {
        val tilted = TWO_STAGE_CASCADE.replace("</rects></_>\n  </features>", "</rects>\n      <tilted>1</tilted></_>\n  </features>")
        assertFailsWith<IllegalArgumentException> { HaarCascade.parse(tilted) }
    }

    @Test
    fun `other feature types are rejected`() {
        assertFailsWith<IllegalArgumentException> { HaarCascade.parse(TWO_STAGE_CASCADE.replace(">HAAR<", ">LBP<")) }
    }

    @Test
    fun `a stage that declares more classifiers than the file has is rejected`() {
        val broken = TWO_STAGE_CASCADE.replace("<maxWeakCount>2</maxWeakCount>", "<maxWeakCount>3</maxWeakCount>")
        assertFailsWith<IllegalArgumentException> { HaarCascade.parse(broken) }
    }

    @Test
    fun `a node that refers to a missing feature is rejected`() {
        assertFailsWith<IllegalArgumentException> { HaarCascade.parse(TWO_STAGE_CASCADE.replace("0 -1 1 4.0e-03", "0 -1 7 4.0e-03")) }
    }

    companion object {
        /** The layout of OpenCV's own cascade files, cut down to two stages and two features. */
        val TWO_STAGE_CASCADE = """
<?xml version="1.0"?>
<opencv_storage>
<cascade type_id="opencv-cascade-classifier"><stageType>BOOST</stageType>
  <featureType>HAAR</featureType>
  <height>20</height>
  <width>20</width>
  <stageParams>
    <maxWeakCount>2</maxWeakCount></stageParams>
  <featureParams>
    <maxCatCount>0</maxCatCount></featureParams>
  <stageNum>2</stageNum>
  <stages>
    <_>
      <maxWeakCount>1</maxWeakCount>
      <stageThreshold>5.0e-01</stageThreshold>
      <weakClassifiers>
        <_>
          <internalNodes>
            0 -1 1 4.0e-03</internalNodes>
          <leafValues>
            1.0e-01 9.0e-01</leafValues></_></weakClassifiers></_>
    <_>
      <maxWeakCount>2</maxWeakCount>
      <stageThreshold>-1.5</stageThreshold>
      <weakClassifiers>
        <_>
          <internalNodes>
            0 -1 0 -2.5e-02</internalNodes>
          <leafValues>
            -5.0e-01 5.0e-01</leafValues></_>
        <_>
          <internalNodes>
            1 0 0 1.0e-01 -1 -2 1 2.0e-01</internalNodes>
          <leafValues>
            3.0e-01 -2.0e-01 7.0e-01</leafValues></_></weakClassifiers></_></stages>
  <features>
    <_>
      <rects>
        <_>
          3 7 14 4 -1.</_>
        <_>
          3 9 14 2 2.</_></rects></_>
    <_>
      <rects>
        <_>
          6 4 12 9 -1.</_>
        <_>
          10 4 4 9 3.</_>
        <_>
          0 0 1 1 0.</_></rects></_>
  </features></cascade>
</opencv_storage>
""".trimIndent()
    }
}
