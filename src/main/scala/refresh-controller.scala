package ledcube

import chisel3._
import chisel3.util._
import ledcube.interfaces._
import ledcube.constants._
import ledcube.constants.TlcConstants._

class RefreshController(max_packet_size : Int = 16) extends Module {
    val io = IO(new Bundle {
        val i2c0 = Output(new I2c())
        val i2c1 = Output(new I2c())
        val i2c2 = Output(new I2c())
        val i2c3 = Output(new I2c())
    })

    val controllers = 
        Vec(Seq.fill(4){ Module(new TlcController(max_packet_size)).io })

    for (i <- 0 until 4) {

        controllers(i).config.mode1 := "h11".U
        controllers(i).config.mode2 := "h00".U
        controllers(i).config.iref := "hCF".U
        controllers(i).config.i2c_config.clock_threshold := 4.U
        controllers(i).config.i2c_config.clock_period := 8.U

        controllers(i).update := true.B
        for (j <- 0 until 16) {
            controllers(i).led_state_in(j) := "b01".U
        }

        i match {
            case 0 => controllers(i).i2c <> io.i2c0
            case 1 => controllers(i).i2c <> io.i2c1
            case 2 => controllers(i).i2c <> io.i2c2
            case 3 => controllers(i).i2c <> io.i2c3
        }
    }
}