package com.horizen.validation

import java.time.Instant

import com.horizen.SidechainHistory
import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.NoncedBox
import com.horizen.chain.SidechainBlockInfo
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.fixtures.{VrfGenerator, _}
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.proposition.Proposition
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.transaction.SidechainTransaction
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, WithdrawalEpochInfo}
import org.junit.Assert.assertTrue
import org.junit.{Before, Ignore, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import scorex.core.consensus.ModifierSemanticValidity
import scorex.util.{ModifierId, bytesToId}

import scala.io.Source

class WithdrawalEpochValidatorTest extends JUnitSuite with MockitoSugar with MainchainBlockReferenceFixture with TransactionFixture with CompanionsFixture{

  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion

  val params: NetworkParams = mock[NetworkParams]
  val historyStorage: SidechainHistoryStorage = mock[SidechainHistoryStorage]
  val history: SidechainHistory = mock[SidechainHistory]

  // Genesis MC block hex created in regtest from MC branch beta_v1 on 25.05.2020
  val mcBlockHex: String = "030000001b199ec4516c4b6021363cd73dcadd5382a1281de6291a46d8a0a6b01a4d490529707a00647aac4ba0f00f64b3b46f6f2d496a030cc23b5808ad78d1fa3606d73622107ce90ea52de0b1b5896459f9b41d0b83076920494643229a5d320a78e6c149e35e030f0f202200b5e4f98dd21ec442183f8bc8ec9a2e483da99e3a773c50390302d7570000240140d14c11a99d48f9244edc0f735729897402236d7a82d9b63fb519d04ab512a2bb39b20201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0502dc000101ffffffff045724b42c000000001976a914a6dcdfc99033d07beb459e5d14fc903296e0cc6e88ac80b2e60e0000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000fcffffff09174cdf38ee44a2d9ea76363c5f121afadc69dfc7d9d0c4ab0e35e6970eb99108000000006a4730440220612dba5d3968c27a9d162863f4a30d0ee597e3d1628da6a854fd3020eef66a67022034a37a3fa5f1fecd587e74a3f5d3d9f86af1e4abece4ed4c24bf106d3b0d1c0b012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff64a2205070fe9747fb641b1614027b8bf1fa18a95cc808b05cb5206dc27e66ca000000006b48304502210093a89f7818dd71469ba6d2427f6a7cd59a8502d94b4c90f3eba88e722e1e94c302207625ae9a3c1a5ddb7ade8ade70eb89d772743fcb3b4e79af43bb24b9c66d104e012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffffe8ac048bfa7286d87756857d4d3a78291cba719c63a32c070982a5fc2d226e1c000000006a473044022038c5996d46c02269d435db49201ec6dc371e2fe873c258a6fe768bf71ffd00fc02200413e4610c064bbbac529f8d3bb68f118814b00cf6d362af2a866535e149ffc3012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff9c1192ad64c623a0e30edf4cc38e8122f5b47f0c5ce954555999bbcc6d54cb84000000006b483045022100c61768c9315bc074dc089bb74002e2e63cf92b22b6acf9e6200a43bb755825e8022008570a0e68a2bd4da4fee3686efff28a5e65902924571ace2475e256fa23f15e012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff04d9f29d257d289b5693a0da5bf9c01e620b4de046b918f92b22ec3ae2c166fd000000006a4730440220156bc610578fb1c5f8da019760a24c0634e3536225ec4b7f6aa89ad8c56a72f602202034a699c347baaaed72fbbe8f9cb453d9d72fa1e44e2623c6e19ddf0a92da6a012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff97c2f86e46e775b320e22630dbe84e7a04f5103bb1b7f54797bd76ba843ca44e000000006a473044022071b9c641218c8153e855615996803cd0885dc7b0826b1aab3cb4c4c9be638f64022064c3e1a5b22ce38ae566c1f73924666b49876e06c283529a8875d9e74be0300c012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffffd1d57576ba121a45e4ad340975abdebf24d0d0dab85dd17440d78aaf13cc0b38000000006a47304402202fd572e2c941feb8710283a6e95a8d39964f01873cf7506dcdc828d914ac4f4302204d0b6bcf9d05e902e9a05cc96d41c2a19af0bf1055177f8e1111b2a8e2e5de2b012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffffce4e0c6cd8f1c1b2ab2350d72383aaefe8d925128b126d40d7ef346910a6ea8a000000006a47304402205e2bd14ea3787febea21803a135a882b553fff7758fc2370af8055c1694d8ba3022034382608166beaa057324ecdaa0f6747022f2e0f7f2b3539426b5f62440beb67012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff3d71485da571aacb77f33117e4aa59cf02eec6ad38210c110ca09e9258df1b33000000006b48304502210084dcc8d11fba1b4b2c2b74cca2128b40bdc563ed00a2d725144117b7f3b29c34022074edb63dd8422649237d421e32b6aa3d8db75c0f8bcdf3a809ea1b003621de96012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff01696b7d01000000003c76a9144129792b8ec0606c8ddb53a8c011f257dd2292e288ac20bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b4010a00000000e40b5402000000acb24b2f08e8e56fe1784f1600879c697c7cd90846e076724b090fd72206b1a5c1dd2de641154fd54de4cf60ea3f5b9e7135787ecb9fcce75de5c41f974fd0cbf70af51ba99b1b8d591d237091414051d2953b7d75e16d89be6fe1cf0bfc63a244f6f51159061875ff1922c3d923d365370ac2605c19e03d674bf64af9e91e00003a6fe5d3f1bcddf09faee1866e453f99d4491e68811bc1a7d5695955e4f8f456627f546bdbbbd026c1b6ee35e2f65659cbcd32406026ebb8f602c86d3f42499f8412dc3ebe664ce188c69360f13dddbd577513171f49423d51ff9578b15901000060b866a7695601aa41cb7775ed16208d0f79e8c19376d99b3cbab937f3a271a7347a9eceab34d14ddcd0aa7936869cc620d5f5a8a44c78a0fc621edfc69e2751ad4192fd57a57eb5d8e798bd15773b20a29cd260755b6c5ec7b051ecd4685e01005e7b462cc84ae0faaa5884bd5c4a5a5edf13db210599aeeb4d273c0f5f32967b7071ce2b4d490b9f08f6ce66a8405735c79197cd6773d1c5aeb2a38da1c102df07b05879c77198e5aafa7feed25d4137e86b3d98d9edd9547a460f1615b10000ee9570fbffedd44170477b37500a0a1cb3f94b6361f10f8a68c4075fbc17542d7174b3d95e12ddb8aea5d6b6c53c1df6c8f60010cd2e69902ba5e89e86747569463a23254730fc8d2aabf39648a505df9dcce461443b181ef3eda46074070000550836db2c97820971db6b1421e348d946ed4d3f255295abea46556615e3123de33ec56f784f70302901a4bc10c79c6a8b1e32477aeff9fba75876592981b678fc5a2703ac0b3055e567a6cb1ebab578fc4f9121fd968680250696cb85790000078fcfb60bdfc79aa1e377cb120480538e0236156f23129a88824ca5a1d77e371e5e98a16e6f32087c91aa02a4f5e00e412e515c3b678f6535141203c6886c637b626a2ada4062d037503359a680979091c68941a307db6e4ed8bc49d21b00002f0e6f88fb69309873fdefb015569e5511fb5399295204876543d065d177bf36ab79183a7c5e504b50691bc5b4ed0293324cfe2555d3fc8e39485822a90a91afcd4ef79ec3aefbd4cbe25cbccd802d8334ce1dce238c3f7505330a14615500001f89fbe1922ab3aa31a28fd29e19673714a7e48050dee59859d68345bb7bee7d5e888d8b798a58d7c650f9138304c05a92b668294c6114185ccb2c67ce0bbbb7e1dcbb6d76f5cacd7c9732a33b21d69bd7a28c9cca68b5735d50413862bc0100308bb0dd0bd53f3d1134966702dd3c7cc8b58b270a6996a646493250b0d5f3978d0c971f8fa7a0c958f3efe2fa5269244973fafb701c2eb66dd25901f93d677ab6c538c1ed11f115e52d3f2c7087ea40c3e8cd089376baa38842e9429b5f0000d19a8d874d791f952f13d3c8ecd92e44009c09815e5ae6a8e5def7ea52fe3de4accfb5ba2aa401fbcec14b069cd0dc0f66ab025b45ef9831a26acf58673db7487043654e7980fcb2b6c1bd7593a4dfff810436f653e309121c7ccf2df70b010000732254ec6df184be360cd9ed383ed7c8c236d7761cfc0ce4e7f0cac5a06f4edab9cfc75a7dc1449c0e18ed9564c974c2e1b6847c637f74e5d391cbc80fc6e672ffd66b5ce4fb73bda8359ab8a0ea1e855df1e07d82f93c935c7e1a9a55c5000065efdbb7c3e82291a482b2f24cbd46f4dd02c370cf6dcfe8fb3c00b8b004b5ad51369b1f1b134a824d1f16d72ca6a27ba2d6190150329139cf2c6d9e5a14722f8d39b96b882c1f60a7b230e929819e2abe1cd9d7f3e8c726b1a94d20c8010100732c396eca6ffa1bf851cef449f2f087edd93e4f641b4bd93a482d9f129e675aedb688993d4e2cee824d2803301364ba10fbb66895927adb53bad8aefe8a1caab6f4ccb45883e414a1223ac7f90a89087cd752dfa0c7b3e19bbae000edd5000028d1d23c627d1252d2a2a20a246af2280f50e3fde667873aadd9893ba6833118358398e7428e717128f764714a8d52b090c1f554f58e25ea815338d7bc7326c949567e74f2f2ab3c88f5075fea75594608b8937c9059a42d712ffbd1bd980100000000000250c1a474689e375a309446e5cdd3a0c26cecdcff5c7b8cdc0728868983f1a35a49e3a1bae6f969c3d47356c08d3d169d2c0a2be908d82cd35f41a23d8c2924a9f790ab3a00d53061d440a176670d6a32de2ecd19cf8a9774729c09a6ea4d0100d8838bf55d95521291da12294b302c66042eda0dc2acc79360a1fdd8c9a366fa790c52bf926c2d96b5ba88a3a443487c5235f7c476f350c2101cfbe3bd0361dd291ebc5e42c097a158704b71006886a3662ca6db7d816b4ad12444835d89000000795ce2b34aef921ccb3d9b9695f5d3fe0a03743c955cfcf01f8a1815a7c8b03de85fe15201d4b4b6f401cb334a6988ea5bde8986a468c47c3c6a5ae96a3160ff15e06699ea82bd40c0d5547fe1be77af7817861bbfcca3f4232f05a9cec800006c216565cee4d57b32d2d70bb3cb8d4a967c0eb5d7137b2ec58466f3d4d3b5375e4baa823bcc29c6ad877d9708cd5dc1c31fa3883a80710431110c4aa22e97b67fa639f54e86cfab87187011270139df7873bed12f6fb8cd9ab48f389338010000007500000000"

  @Before
  def setUp(): Unit = {
    Mockito.when(history.storage).thenReturn(historyStorage)
  }

  @Test
  def genesisBlockValidation(): Unit = {
    val validator = new WithdrawalEpochValidator(params)

    // Test 1: invalid genesis block - no MainchainBlockReferenceData
    val (forgerBox1, forgerMeta1) = ForgerBoxFixture.generateForgerBox(32)
    var block: SidechainBlock = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      Seq(),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta1.blockSignSecret,
      forgerBox1,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(params.sidechainGenesisBlockId).thenReturn(block.id)
    assertTrue("Sidechain genesis block with no MainchainBlockReferenceData expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 2: invalid genesis block - multiple MainchainBlockReferenceData
    val (forgerBox2, forgerMeta2) = ForgerBoxFixture.generateForgerBox(322)
    var mcRefs: Seq[MainchainBlockReference] = Seq(generateMainchainBlockReference(), generateMainchainBlockReference())

    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      mcRefs.map(_.header),
      Seq(),
      forgerMeta2.blockSignSecret,
      forgerBox2,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(params.sidechainGenesisBlockId).thenReturn(block.id)
    assertTrue("Sidechain genesis block with multiple MainchainBlockReferenceData expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 3: invalid genesis block - 1 MainchainBlockReferenceData without sc creation tx
    val (forgerBox3, forgerMeta3) = ForgerBoxFixture.generateForgerBox(32)
    mcRefs = Seq(generateMainchainBlockReference())

    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      mcRefs.map(_.header),
      Seq(),
      forgerMeta3.blockSignSecret,
      forgerBox3,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(params.sidechainGenesisBlockId).thenReturn(block.id)
    assertTrue("Sidechain genesis block with 1 MainchainBlockReferenceData without sc creation inside expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: NoSuchElementException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 4: valid genesis block with 1 MainchainBlockReferenceData with sc creation tx with INVALID withdrawalEpochLength (different to the one specified in params)
    val scIdHex = "2f7ed2e07ad78e52f43aafb85e242497f5a1da3539ecf37832a0a31ed54072c3"
    val scId = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex))
    val mcBlockRefRegTestParams = RegTestParams(scId.data)
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcBlockRef = MainchainBlockReference.create(mcBlockBytes, mcBlockRefRegTestParams).get
    mcRefs = Seq(mcBlockRef)

    val (forgerBox4, forgerMeta4) = ForgerBoxFixture.generateForgerBox(324)
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      mcRefs.map(_.header),
      Seq(),
      forgerMeta4.blockSignSecret,
      forgerBox4,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      mcBlockRefRegTestParams
    ).get

    Mockito.when(params.sidechainGenesisBlockId).thenReturn(block.id)
    Mockito.when(params.withdrawalEpochLength).thenReturn(123)
    assertTrue("Sidechain genesis block with 1 MainchainBlockReferenceData with sc creation inside with incorrect withdrawalEpochLength expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 5: the same as above but with valid withdrawalEpochLength specified in params / sc creation
    Mockito.when(params.withdrawalEpochLength).thenReturn(10)
    assertTrue("Sidechain genesis block with 1 MainchainBlockReferencesData with sc creation with correct withdrawalEpochLength inside expected to be valid.", validator.validate(block, history).isSuccess)
  }

  @Test
  def blockValidation(): Unit = {
    val validator = new WithdrawalEpochValidator(params)
    val withdrawalEpochLength = 100
    Mockito.when(params.sidechainGenesisBlockId).thenReturn(bytesToId(new Array[Byte](32)))
    Mockito.when(params.withdrawalEpochLength).thenReturn(withdrawalEpochLength)


    // Test 1: invalid block - no MainchainBlockReferencesData, parent is missed
    val (forgerBox1, forgerMeta1) = ForgerBoxFixture.generateForgerBox(1)

    var block: SidechainBlock = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      Seq(),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta1.blockSignSecret,
      forgerBox1,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn(None)
    assertTrue("Sidechain block with missed parent expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }

    // Test 2: valid block - no MainchainBlockReferenceData, parent is the last block of previous epoch
    val (forgerBox2, forgerMeta2) = ForgerBoxFixture.generateForgerBox(22)

    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      Seq(),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta2.blockSignSecret,
      forgerBox2,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength), Option(VrfGenerator.generateVrfOutput(0)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with no MainchainBlockReferenceData expected to be valid.", validator.validate(block, history).isSuccess)


    // Test 3: valid block - no MainchainBlockReferenceData, parent is in the middle of the epoch
    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength / 2), Option(VrfGenerator.generateVrfOutput(1)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with no MainchainBlockReferenceData expected to be valid.", validator.validate(block, history).isSuccess)


    // Test 4: valid block - no MainchainBlockReferenceData, parent is at the beginning of the epoch
    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(),  Seq(),
        WithdrawalEpochInfo(1, 0), Option(VrfGenerator.generateVrfOutput(2)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with no MainchainBlockReferenceData expected to be valid.", validator.validate(block, history).isSuccess)


    // Test 5: valid block - with MainchainBlockReferenceData, that are in the middle of the epoch
    val (forgerBox5, forgerMeta5) = ForgerBoxFixture.generateForgerBox(3524)
    var mcRefs: Seq[MainchainBlockReference] = Seq(generateMainchainBlockReference(), generateMainchainBlockReference()) // 2 MC block refs
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data), // 2 MainchainBlockReferenceData
      Seq(),
      Seq(), // No MainchainHeaders - no need of them
      Seq(),
      forgerMeta5.blockSignSecret,
      forgerBox5,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength - 3), // lead to the middle index -> no epoch switch
        Option(VrfGenerator.generateVrfOutput(3)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with MainchainBlockReferenceData that are in the middle of the epoch expected to be valid.", validator.validate(block, history).isSuccess)


    // Test 6: valid block - without SC transactions and with MainchainBlockReferenceData, that lead to the end of the epoch
    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength - 2), // lead to the last epoch index -> no epoch switch
        Option(VrfGenerator.generateVrfOutput(4)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with MainchainBlockReferenceData that lead to the finish of the epoch expected to be valid.", validator.validate(block, history).isSuccess)


    // Test 7: invalid block - without SC transactions and with MainchainBlockReferenceData, that lead to switching the epoch
    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength - 1), // lead to the switching of the epoch
        Option(VrfGenerator.generateVrfOutput(5)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with MainchainBlockReferenceData that lead to epoch switching expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 8: valid block - with SC transactions and MainchainBlockReferenceData, that are in the middle of the epoch
    val (forgerBox8, forgerMeta8) = ForgerBoxFixture.generateForgerBox(324)
    mcRefs = Seq(generateMainchainBlockReference(), generateMainchainBlockReference()) // 2 MC block refs

    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data), // 2 MainchainBlockReferenceData
      Seq(getRegularTransaction.asInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]]), // 1 SC Transaction
      Seq(),
      Seq(),
      forgerMeta8.blockSignSecret,
      forgerBox8,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength - 3), // lead to the middle index -> no epoch switch
        Option(VrfGenerator.generateVrfOutput(5)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with SC transactions andMainchainBlockReferenceData that are in the middle of the epoch expected to be valid.", validator.validate(block, history).isSuccess)


    // Test 9: invalid block - with SC transactions and MainchainBlockReferenceData, that lead to the end of the epoch (no sc tx allowed)
    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength - 2), // lead to the last epoch index -> no epoch switch
        Option(VrfGenerator.generateVrfOutput(6)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with SC transactions and MainchainBlockReferenceData that lead to the finish of the epoch expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 10: invalid block - with SC transactions and MainchainBlockReferenceData, that lead to switching the epoch (no sc tx and no switch allowed)
    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength - 1), // lead to the switching of the epoch
        Option(VrfGenerator.generateVrfOutput(7)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with SC transactions and MainchainBlockReferenceData that lead to the epoch switching expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 11: invalid block - with 1 MainchainBlockReferenceData with sc creation tx with declared sidechain creation output
    val scIdHex = "2f7ed2e07ad78e52f43aafb85e242497f5a1da3539ecf37832a0a31ed54072c3"
    val scId = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex))
    val mcBlockRefRegTestParams = RegTestParams(scId.data)
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcBlockRef = MainchainBlockReference.create(mcBlockBytes, mcBlockRefRegTestParams).get

    val (forgerBox11, forgerMeta11) = ForgerBoxFixture.generateForgerBox(32114)
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      Seq(mcBlockRef.data),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta11.blockSignSecret,
      forgerBox11,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      mcBlockRefRegTestParams
    ).get

    assertTrue("Sidechain non-genesis block with 1 MainchainBlockReferenceData with sc creation inside expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 12: invalid block - with 2 MainchainBlockReferenceData, the second one is with sc creation tx
    val (forgerBox12, forgerMeta12) = ForgerBoxFixture.generateForgerBox(31224)
    mcRefs = Seq(generateMainchainBlockReference(blockHash = Some(mcBlockRef.header.hashPrevBlock)), mcBlockRef)

    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta12.blockSignSecret,
      forgerBox12,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      mcBlockRefRegTestParams
    ).get

    assertTrue("Sidechain non-genesis block with 2 MainchainBlockReferenceData, the second one with sc creation inside expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 13: invalid block - with 3 MainchainBlockReferenceData, the second one is with sc creation tx
    val (forgerBox13, forgerMeta13) = ForgerBoxFixture.generateForgerBox(32413)
    mcRefs = Seq(generateMainchainBlockReference(blockHash = Some(mcBlockRef.header.hashPrevBlock)), mcBlockRef, generateMainchainBlockReference(parentOpt = Some(new ByteArrayWrapper(mcBlockRef.header.hash))))
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta13.blockSignSecret,
      forgerBox13,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      mcBlockRefRegTestParams
    ).get

    assertTrue("Sidechain non-genesis block with 3 MainchainBlockReferenceData, the second one with sc creation inside expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 14: valid block - with 2 MainchainBlockReferenceData, that lead to epoch ending, and 2 more MainchainHeaders
    val (forgerBox14, forgerMeta14) = ForgerBoxFixture.generateForgerBox(35274)
    mcRefs = Seq(generateMainchainBlockReference(), generateMainchainBlockReference(), generateMainchainBlockReference(), generateMainchainBlockReference()) // 4 MC block refs
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.take(2).map(_.data), // 2 MainchainBlockReferenceData
      Seq(),
      mcRefs.map(_.header), // 4 MainchainHeaders, from different withdrawal epochs
      Seq(),
      forgerMeta14.blockSignSecret,
      forgerBox14,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength - 2), // lead to the last epoch index -> no epoch switch
        Option(VrfGenerator.generateVrfOutput(7)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with MainchainBlockReferenceData that lead to the finish of the epoch and 2 more MainchainHeaders expected to be valid.",
      validator.validate(block, history).isSuccess)
  }
}
