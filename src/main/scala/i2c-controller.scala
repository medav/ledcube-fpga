package ledcube

import chisel3._
import chisel3.util._
import ledcube.interfaces._

class I2cController(max_packet_size : Int = 16) extends Module {
    val io = IO(new Bundle {
        val config = Input(new I2cConfig())
        val request = Flipped(DecoupledIO(new I2cPacket(max_packet_size)))
        val i2c = new I2c()
        val error = Output(Bool())
    })

    val s_idle :: s_start :: s_write :: s_nack :: s_error :: s_stop :: Nil = Enum(6)
    val state = RegInit(s_idle)

    // This register holds the whole packet currently being transmitted. Note
    // that _all_ packets have a address and header byte which is extracted from
    // the packet bundle. This register treats the address, header, and payload
    // all as one packet (Hence the +2)
    val packet = Reg(Vec(max_packet_size + 2, UInt(8.W)))

    // Total packet size (address + header + payload size)
    val packet_size = RegInit(0.U(8.W))

    //
    // This module uses several counters to produce a I2C protocal with
    // configurable data-rate given by io.clock_threshold and io.clock_period.
    // Both config signals are in terms of clock cycles of whatever clock this
    // module is running on. The term 'beat' is used to describe a "clock cycle"
    // of the I2C protocol, while 'cycle' and 'clock cycle' refer to cycles as
    // it pertains to this module.
    //

    // Counts actual cycles in the current beat.
    val clock_counter = RegInit(0.U(16.W))

    // Tracks which beat this module is on. This is used to select which bit of
    // the current output byte is to be written to SDA.
    val beat_counter = RegInit(0.U(16.W))

    // Tracks which byte of the packet this module is currently writing. This is
    // used to select which byte of the data packet is to be written.
    val data_counter = RegInit(0.U(16.W))

    // This signal tracks when the cycle count has crossed the threshold value.
    // This is used to indicate where the I2C SCL line should go to logic HIGH.
    val pulse = clock_counter >= io.config.clock_threshold

    // This signal is used to track when the rising edge of SCL occurs for the
    // purpose of recording NACKs.
    val rising_edge = clock_counter === io.config.clock_threshold

    // This indicates when the current I2C beat is finished and the next bit of
    // data should be written out to the SDA line (in a new beat).
    val beat_finished = clock_counter >= io.config.clock_period

    val next_data = Vec(packet(data_counter + 1.U).toBools)
    val data_reg = RegInit(Vec(Seq.fill(8)(false.B)))
    val data_out = Wire(Bool())

    //
    // Constants used for determining state of the I2C pins.
    //  HIGH: Pin output is disabled. Externel pull-up resistor hold the line
    //      high assuming no one else on the bus is pulling it down.
    //
    //  LOW: Pin output is disabled but this module holds it low.
    //
    //  ACTIVE: Pin output from this module is enabled.
    //

    val SCL_HIGH = 0.U
    val SCL_ACTIVE = 1.U
    val SDA_HIGH = 0.U
    val SDA_LOW = 1.U
    val SDA_ACTIVE = 2.U

    //
    // Use state signals to determine what value to drive sda and scl with.
    //

    val sda_state = Wire(UInt())
    val scl_state = Wire(UInt())

    io.i2c.scl.out :=
        MuxLookup(scl_state, 1.U, Array(SCL_HIGH -> 1.U, SCL_ACTIVE -> pulse))

    io.i2c.sda.out :=
        MuxLookup(sda_state, 1.U,
            Array(SDA_HIGH -> 1.U, SDA_LOW -> 0.U, SDA_ACTIVE -> data_out))

    //
    // Defaults
    //

    sda_state := SDA_HIGH
    scl_state := SCL_HIGH
    io.request.ready := false.B
    io.error := false.B
    io.i2c.resetn := true.B
    data_out := data_reg(7.U - beat_counter)
    clock_counter := clock_counter + 1.U

    //
    // State transition logic
    //

    switch (state) {
        is (s_idle) {
            // The I2C bus is idle when both SDA and SCL are high.
            sda_state := SDA_HIGH
            scl_state := SCL_HIGH

            // Can accept a new request here.
            io.request.ready := pulse

            when (io.request.fire()) {
                state := s_start

                //
                // N.B. The address and header are added into the internal
                // packet register and so the packet size has 2 bytes + payload
                // size.
                //

                packet_size := io.request.bits.size + 2.U

                data_counter := 0.U
                clock_counter := 0.U
                beat_counter := 0.U

                //
                // Address and header are the first two bytes to be written.
                //

                packet(0) := io.request.bits.address
                packet(1) := io.request.bits.header

                for (i <- 0 until max_packet_size) {
                    packet(i + 2) := io.request.bits.payload(i)
                }
            }
        }

        is (s_start) {

            //
            // I2C write start condition is signaled by holding SDA low and SCL
            // high. After half a beat, SCL is tied low (by state transition to
            // s_write) and the first beat begins.
            //

            sda_state := SDA_LOW
            scl_state := SCL_HIGH

            //
            // pulse signals when half the beat is over. Transition to s_write
            // here and start outputting data.
            //

            when (pulse) {
                clock_counter := 0.U
                data_reg := Vec(packet(0).toBools)
                state := s_write
            }
        }

        is (s_write) {
            sda_state := SDA_ACTIVE
            scl_state := SCL_ACTIVE

            //
            // When the clock is to be asserted (signaling the latching of sda
            // by the slave) the slave might hold scl to ground to to indicate
            // it can't accept the data yet (clock stretching). In this case,
            // clock_counter simply holds its value until the slave releases
            // the clock.
            //

            when (pulse && !io.i2c.scl.in) {
                clock_counter := clock_counter
            }
            .elsewhen (beat_finished) {
                clock_counter := 0.U

                when (beat_counter === 7.U) {
                    beat_counter := 0.U
                    state := s_nack
                }
                .otherwise {
                    beat_counter := beat_counter + 1.U
                }
            }
        }

        is (s_nack) {
            sda_state := SDA_HIGH
            scl_state := SCL_ACTIVE

            when (beat_finished) {
                clock_counter := 0.U

                when (data_counter === (packet_size - 1.U)) {
                    state := s_stop
                }
                .otherwise {
                    data_counter := data_counter + 1.U
                    data_reg := next_data
                    state := s_write
                }
            }

            //
            // If SDA is not held low (by the target device) when SCL 
            // transitions to HIGH, this indicates a failed NACK.
            //

            when (rising_edge && io.i2c.sda.in) {
                state := s_error
            }
        }

        is (s_error) {

            //
            // This state is reached when a byte finishes transmition and no
            // NACK is received from the target device.
            //

            io.error := true.B
        }

        is (s_stop) {

            //
            // The I2C stop condition occurs when SDA is held low for one clock
            // beat then finally released (pulled high by the pull-up resistor).
            // Once this beat finishes, this module will transition to s_idle
            // where SDA will go HIGH, completing the stop condition.
            //

            scl_state := SCL_ACTIVE
            sda_state := SDA_LOW

            when (beat_finished) {
                state := s_idle
                clock_counter := 0.U
            }
        }
    }
}