package com.horizen.forge

import com.horizen.block.SidechainBlock

sealed trait ForgeResult

case class ForgeSuccess(block: SidechainBlock) extends ForgeResult {override def toString: String = s"Successfully generated block ${block.id.toString}"}
case object SkipSlot extends ForgeResult {override def toString: String = s"Skipped slot for forging"}
case class ForgeFailed(ex: Throwable) extends ForgeResult {override def toString: String = s"Failed block generation due ${ex}"}
