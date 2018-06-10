package ledcube

import chisel3._
import chisel3.util._
import ledcube.interfaces._
import ledcube.constants._
import ledcube.constants.TlcConstants._

class RefreshController() extends Module {
    val io = IO(new Bundle {
        val i2c0 = new I2c()
        val i2c1 = new I2c()
        val i2c2 = new I2c()
        val i2c3 = new I2c()
    })

    val controllers = 
        Vec(Seq.fill(4){ Module(new TlcController()).io })

    val counter_reg = RegInit(0.U(32.W))

    counter_reg := counter_reg + 1.U

    for (i <- 0 until 4) {
        controllers(i).config.mode1 := "h11".U
        controllers(i).config.mode2 := "h00".U
        controllers(i).config.iref := "hCF".U
        controllers(i).config.i2c_config.clock_threshold := 50.U
        controllers(i).config.i2c_config.clock_period := 100.U

        controllers(i).update := true.B

        for (j <- 0 until 16) {
            controllers(i).led_state_in(j) := (j << 4).U + (counter_reg >> 20.U)
        }

        i match {
            case 0 => controllers(i).i2c <> io.i2c0
            case 1 => controllers(i).i2c <> io.i2c1
            case 2 => controllers(i).i2c <> io.i2c2
            case 3 => controllers(i).i2c <> io.i2c3
        }
    }
}