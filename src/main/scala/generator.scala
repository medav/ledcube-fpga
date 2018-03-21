package ledcube

import chisel3._
import chisel3.util._

object Ledcube extends App {
  chisel3.Driver.execute(args, () => new RefreshController)
}