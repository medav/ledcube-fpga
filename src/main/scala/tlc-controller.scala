package ledcube

import chisel3._
import chisel3.util._
import ledcube.interfaces._
import ledcube.constants._
import ledcube.constants.TlcConstants._

class TlcController(max_packet_size : Int = 16) extends Module {
    val io = IO(new Bundle {
        val config = Input(new TlcConfig())
        val update = Input(Bool())
        val ready = Output(Bool())

        val hard_resetn = Output(Bool())
        val i2c = Output(new I2c())

        val led_state_in = Input(Vec(16, UInt(2.W)))
    })

    val s_reset :: s_setup_mode :: s_setup_iref :: s_ready ::  Nil = Enum(6)
    val state = RegInit(s_reset)

    val i2c_ctrl = Module(new I2cController(max_packet_size))
    i2c_ctrl.config <> io.config.i2c_config
    i2c_ctrl.i2c <> io.i2c

    io.hard_resetn := true.B

    // Default values for request packet
    i2c_ctrl.request.valid := false.B
    i2c_ctrl.request.bits.size := 0.U
    i2c_ctrl.request.bits.address := 0.U
    i2c_ctrl.request.bits.header := 0.U

    for (i <- 0 until max_packet_size) {
        i2c_ctrl.request.bits.payload(0) := 0.U
    }

    when (i2c_ctrl.error) {
        state := s_error
    }
    
    def MakeRequest(size : Int, address : UInt, header : UInt, next_state : UInt) = {
        i2c_ctrl.request.valid := true.B
        i2c_ctrl.request.bits.size := size.U
        i2c_ctrl.request.bits.address := address
        i2c_ctrl.request.bits.header := header

        when (i2c_ctrl.request.fire()) {
            state := s_setup
        }
    }

    switch (state) {
        is (s_reset) {
            MakeRequest(1, "hD6".U, "hA5".U, s_setup_mode)
            i2c_ctrl.request.bits.payload(0) := "h5A".U
        }
        is (s_setup_mode) {
            MakeRequest(2, ALLCALLADDR, AUTOINC_ALL | TlcRegisters.MODE1, s_setup_iref)
            i2c_ctrl.request.bits.payload(0) := io.config.mode1
            i2c_ctrl.request.bits.payload(1) := io.config.mode2
        }
        is (s_setup_iref) {
            MakeRequest(1, ALLCALLADDR, AUTOINC_ALL | TlcRegisters.MODE1, s_ready)
            i2c_ctrl.request.bits.payload(0) := io.config.iref
        }
        is (s_ready) {
            i2c_ctrl.request.valid := io.update
            i2c_ctrl.request.bits.size := 4.U
            i2c_ctrl.request.bits.address := ALLCALLADDR
            i2c_ctrl.request.bits.header := AUTOINC_ALL | TlcRegisters.LEDOUT0

            for (i <= 0 until 4) {
                i2c_ctrl.request.bits.payload(i) := 
                    Cat(
                        io.led_state_in(i * 4 + 3), 
                        io.led_state_in(i * 4 + 2), 
                        io.led_state_in(i * 4 + 1), 
                        io.led_state_in(i * 4 + 0))
            }

            io.ready := i2c_ctrl.io.ready
        }
        is (s_error) {
            io.hard_resetn := false.B
        }
    }
}