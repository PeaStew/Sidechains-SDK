package com.horizen.storage

import com.horizen.SidechainTypes
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.fixtures.{BoxFixture, IODBStoreFixture}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, ForgerBoxMerklePathInfo, MerklePath, Pair}
import org.junit.{Before, Test}
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.mockito.{ArgumentMatchers, Mockito}
import java.util.{ArrayList => JArrayList, Optional => JOptional}

import com.horizen.box.ForgerBox

import scala.collection.JavaConverters._


class ForgingBoxesInfoStorageTest extends JUnitSuite
  with IODBStoreFixture
  with MockitoSugar
  with SidechainTypes
  with BoxFixture {

  @Test
  def updateForgerBoxes(): Unit = {
    val mockedStorage: Storage = mock[IODBStoreAdapter]
    val forgingBoxesMerklePathStorage = new ForgingBoxesInfoStorage(mockedStorage)

    val version = getVersion
    val version2 = getVersion
    val forgerBox = getForgerBox
    val forgerBoxesToAppend: Seq[ForgerBox] = Seq(forgerBox)
    val boxIdsToRemove: Seq[Array[Byte]] = Seq(getRandomBoxId(444L))

    // mock physical storage to return nothing on forger box seq request.
    Mockito.when(mockedStorage.get(
      ArgumentMatchers.any[ByteArrayWrapper]()))
      .thenAnswer( answer => {
        val key: ByteArrayWrapper = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]
        assertEquals("Store get different key expected.", forgingBoxesMerklePathStorage.forgerBoxesKey, key)
        JOptional.empty
    })

    Mockito.when(mockedStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.anyList[Pair[ByteArrayWrapper, ByteArrayWrapper]](),
      ArgumentMatchers.anyList[ByteArrayWrapper]()))
      // For Test 1:
      .thenAnswer(answer => {
        val actualVersion = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]
        val actualToUpdate = answer.getArgument(1).asInstanceOf[java.util.List[Pair[ByteArrayWrapper, ByteArrayWrapper]]]
        val actualToRemove = answer.getArgument(2).asInstanceOf[java.util.List[ByteArrayWrapper]]

        assertEquals("Store update(...) actual Version is wrong.", version, actualVersion)
        assertEquals("Store update(...) actual list to update expected to have different size.", 1, actualToUpdate.size())
        assertTrue("Store update(...) actual list to remove expected to be empty.", actualToRemove.isEmpty)
      })
      // For Test 2: verify that nothing changed
      .thenAnswer(answer => {
        val actualVersion = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]
        val actualToUpdate = answer.getArgument(1).asInstanceOf[java.util.List[Pair[ByteArrayWrapper, ByteArrayWrapper]]]
        val actualToRemove = answer.getArgument(2).asInstanceOf[java.util.List[ByteArrayWrapper]]

        assertEquals("Store update(...) actual Version is wrong.", version2, actualVersion)
        assertTrue("Store update(...) actual list to update expected to be empty.",actualToUpdate.isEmpty)
        assertTrue("Store update(...) actual list to remove expected to be empty.", actualToRemove.isEmpty)
      })
      // For Test 3:
      .thenAnswer(answer => {
        throw new IllegalArgumentException("exception")
      })


    // Test 1: successful update of physical Store
    assertTrue("updateForgerBoxes expected to be successful.",
      forgingBoxesMerklePathStorage.updateForgerBoxes(version, forgerBoxesToAppend, boxIdsToRemove).isSuccess)


    // Test 2: successful update of physical Store, nothing changed in toUpdate list
    assertTrue("updateForgerBoxes expected to be successful.",
      forgingBoxesMerklePathStorage.updateForgerBoxes(version2, Seq(), boxIdsToRemove).isSuccess)


    // Test 3: failed to update of physical Store
    assertTrue("updateForgerBoxes expected to fail.",
      forgingBoxesMerklePathStorage.updateForgerBoxes(version, forgerBoxesToAppend, boxIdsToRemove).isFailure)
  }

  @Test
  def updateForgerBoxMerklePathInfo(): Unit = {
    val mockedStorage: Storage = mock[IODBStoreAdapter]
    val forgingBoxesInfoStorage = new ForgingBoxesInfoStorage(mockedStorage)

    // Prepare data to update.
    val epochNumber = ConsensusEpochNumber @@ 100
    val boxMerklePathInfoSeq = Seq(
      ForgerBoxMerklePathInfo(
        getForgerBox,
        new MerklePath(new JArrayList())
      )
    )
    val version = getVersion

    Mockito.when(mockedStorage.lastVersionID()).thenReturn(JOptional.ofNullable[ByteArrayWrapper](null))

    Mockito.when(mockedStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.anyList[Pair[ByteArrayWrapper, ByteArrayWrapper]](),
      ArgumentMatchers.anyList[ByteArrayWrapper]()))
      // For Test 1:
      .thenAnswer(answer => {
      val actualToUpdate = answer.getArgument(1).asInstanceOf[java.util.List[Pair[ByteArrayWrapper, ByteArrayWrapper]]]
      val actualToRemove = answer.getArgument(2).asInstanceOf[java.util.List[ByteArrayWrapper]]

      assertEquals("Store update(...) actual list to update size expected to be different.", 1, actualToUpdate.size())
      assertEquals("Different toUpdate epoch key expected.", forgingBoxesInfoStorage.epochKey(epochNumber), actualToUpdate.get(0).getKey)
      assertEquals("Different toUpdate value expected.",
        new ByteArrayWrapper(forgingBoxesInfoStorage.forgerBoxMerklePathInfoListSerializer.toBytes(boxMerklePathInfoSeq.asJava)),
        actualToUpdate.get(0).getValue)

      assertEquals("Store update(...) actual list to remove size expected to be different.", 1, actualToRemove.size())
      assertEquals("Different toRemove epoch key expected.",
        forgingBoxesInfoStorage.epochKey(ConsensusEpochNumber @@ (epochNumber - forgingBoxesInfoStorage.maxNumberOfStoredEpochs)),
        actualToRemove.get(0))

    })
      // For Test 2:
      .thenAnswer(answer => {
      throw new IllegalArgumentException("exception")
    })


    // Test 1: successful update of physical Store
    assertTrue("Update expected to be successful.", forgingBoxesInfoStorage.updateForgerBoxMerklePathInfo(epochNumber, boxMerklePathInfoSeq).isSuccess)


    // Test 2: failed to update of physical Store
    assertTrue("Update expected to fail.", forgingBoxesInfoStorage.updateForgerBoxMerklePathInfo(epochNumber, boxMerklePathInfoSeq).isFailure)
  }
}
