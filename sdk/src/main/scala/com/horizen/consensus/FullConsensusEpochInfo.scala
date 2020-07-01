package com.horizen.consensus


import com.horizen.utils._


//@TODO move classes to consensus package object

case class ConsensusEpochInfo(epoch: ConsensusEpochNumber, forgersBoxIds: MerkleTree, forgersStake: Long)

case class FullConsensusEpochInfo(stakeConsensusEpochInfo: StakeConsensusEpochInfo, nonceConsensusEpochInfo: NonceConsensusEpochInfo)
