package ledcube

import chisel3._
import chisel3.util._

object LedCube extends App {
  chisel3.Driver.execute(args, () => new LedCubeController)
}