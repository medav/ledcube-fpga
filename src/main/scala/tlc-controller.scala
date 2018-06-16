package ledcube

import chisel3._
import chisel3.util._
import ledcube.interfaces._
import ledcube.constants._
import ledcube.constants.TlcConstants._

class TlcController() extends Module {
    val io = IO(new Bundle {
        val config = Input(new TlcConfig())
        val clear = Input(Bool())
        val update = Input(Bool())
        val ready = Output(Bool())
        val i2c = new I2c()
        val led_state_in = Input(Vec(16, UInt(8.W)))
    })

    val s_reset :: s_setup_mode :: s_setup_iref :: s_ready :: s_clear :: s_update :: s_enable :: s_error :: Nil = Enum(8)
    val state = RegInit(s_reset)

    val i2c_ctrl = Module(new I2cController(16))
    i2c_ctrl.io.config <> io.config.i2c_config
    io.i2c.sda <> i2c_ctrl.io.i2c.sda
    io.i2c.scl <> i2c_ctrl.io.i2c.scl
    io.i2c.resetn := true.B

    // Default values for request packet
    i2c_ctrl.io.request.valid := false.B
    i2c_ctrl.io.request.bits.size := 0.U
    i2c_ctrl.io.request.bits.address := 0.U
    i2c_ctrl.io.request.bits.header := 0.U

    // Default value for ready signal
    io.ready := false.B

    for (i <- 0 until 16) {
        i2c_ctrl.io.request.bits.payload(i) := 0.U
    }

    when (i2c_ctrl.io.error) {
        state := s_error
    }

    def MakeRequest(size : Int, address : UInt, direction : UInt, header : UInt, next_state : UInt) = {
        i2c_ctrl.io.request.bits.size := size.U
        i2c_ctrl.io.request.bits.address := (address << 1) | direction
        i2c_ctrl.io.request.bits.header := header

        when (i2c_ctrl.io.request.fire()) {
            state := next_state
        }
    }

    switch (state) {
        is (s_reset) {

            //
            // In this state, send the TLC59116 specific reset signal over the
            // I2C bus. This performs a soft reset of the driver.
            //

            MakeRequest(1, "h6B".U, WRITE, "hA5".U, s_setup_mode)
            i2c_ctrl.io.request.valid := true.B
            i2c_ctrl.io.request.bits.payload(0) := "h5A".U
        }

        is (s_setup_mode) {

            //
            // In this state, program MODE1 and MODE2 registers on the TLC59116.
            // These two registers hold
            //

            MakeRequest(
                2,
                ALLCALLADDR,
                WRITE,
                AUTOINC_ALL | TlcRegisters.MODE1,
                s_setup_iref)

            i2c_ctrl.io.request.valid := true.B
            i2c_ctrl.io.request.bits.payload(0) := io.config.mode1
            i2c_ctrl.io.request.bits.payload(1) := io.config.mode2
        }

        is (s_setup_iref) {

            //
            // In this state, program the IREF register on the TLC59116.
            //

            MakeRequest(
                1,
                ALLCALLADDR,
                WRITE,
                AUTOINC_ALL | TlcRegisters.MODE1,
                s_ready)

            i2c_ctrl.io.request.valid := true.B
            i2c_ctrl.io.request.bits.payload(0) := io.config.iref
        }

        is (s_ready) {

            //
            // In this state, wait for either a clear or update request.
            //
            // N.B. This state delays all commands by one FPGA cycle, but
            // overall this is 1 / 50,000,000 second extra per command so nbd.
            //

            io.ready := true.B

            when (io.clear) {
                state := s_clear
            }
            .elsewhen (io.update) {
                state := s_update
            }
        }

        is (s_clear) {

            //
            // In this state, make a request to clear all the led output
            // registers to turn off all LEDs.
            //

            MakeRequest(
                4,
                ALLCALLADDR,
                WRITE,
                AUTOINC_ALL | TlcRegisters.LEDOUT0,
                s_ready)

            i2c_ctrl.io.request.valid := true.B

            for (i <- 0 until 4) {
                i2c_ctrl.io.request.bits.payload(i) := 0.U
            }
        }

        is (s_update) {

            //
            // In this state, program the PWM registers with the given 
            // brightness values. 
            //

            MakeRequest(
                16,
                ALLCALLADDR,
                WRITE,
                AUTOINC_ALL | TlcRegisters.PWM0,
                s_enable)

            i2c_ctrl.io.request.valid := true.B

            for (i <- 0 until 16) {
                i2c_ctrl.io.request.bits.payload(i) := io.led_state_in(i)
            }
        }

        is (s_enable) {

            //
            // In this state, program the LEDOUT registers to enable all LEDs
            // in PWD mode. The LEDs will be scaled by the individual PWM
            // registers programmed in the previous state.
            //

            MakeRequest(
                4,
                ALLCALLADDR,
                WRITE,
                AUTOINC_ALL | TlcRegisters.LEDOUT0,
                s_ready)

            i2c_ctrl.io.request.valid := true.B

            for (i <- 0 until 4) {

                //
                // N.B. AA (= b10101010) sets each LED to value "b10" which
                // corresponds to "PWM Mode" for the TLC59116.
                //

                i2c_ctrl.io.request.bits.payload(i) := "hAA".U
            }
        }

        is (s_error) {
            io.i2c.resetn := false.B
            state := s_reset
        }
    }
}