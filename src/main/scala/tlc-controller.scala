package ledcube

import chisel3._
import chisel3.util._
import ledcube.interfaces._

class TlcController(max_packet_size : Int = 16) extends Module {
    val io = IO(new Bundle {
        val config = Input(new TlcConfig())
        val update = Input(Bool())
        val ready = Output(Bool())

        val hard_reset = Output(Bool())
        val sda = Output(Bool())
        val sda_fb = Input(Bool())
        val scl = Output(Bool())

        val led_state_in = Input(Vec(16, UInt(8.W)))
    })

    val s_reset :: s_setup ::  Nil = Enum(6)
}