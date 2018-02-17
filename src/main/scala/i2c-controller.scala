package ledcube

import chisel3._
import chisel3.util._
import ledcube.interfaces._

class I2cController(max_packet_size : Int = 16) extends Module {
    val io = IO(new Bundle {
        val config = Input(new I2cConfig())
        val request = Flipped(DecoupledIO(new I2cPacket(max_packet_size)))
        val sda = Output(Bool())
        val sda_fb = Input(Bool())
        val scl = Output(Bool())
        val error = Output(Bool())
    })

    val s_idle :: s_start :: s_write :: s_nack :: s_error :: s_stop :: Nil = Enum(6)

    val state = RegInit(s_idle)

    val packet = Reg(Vec(max_packet_size + 2, UInt(8.W)))
    val packet_size = Reg(UInt(8.W))

    val sda_state = Wire(UInt())
    val scl_state = Wire(UInt())

    val clock_counter = Reg(UInt(64.W))
    val beat_counter = Reg(UInt(64.W))
    val data_counter = Reg(UInt(64.W))

    val pulse = clock_counter >= io.config.clock_threshold
    val rising_edge = clock_counter === io.config.clock_threshold
    val beat_finished = clock_counter >= io.config.clock_period

    val next_data = Vec(packet(data_counter + 1.U).toBools)
    val data_reg = RegInit(Vec(Seq.fill(8)(false.B)))
    val data_out = Wire(Bool())

    val SCL_HIGH = 0.U
    val SCL_ACTIVE = 1.U
    val SDA_HIGH = 0.U
    val SDA_LOW = 1.U
    val SDA_ACTIVE = 2.U

    io.scl := 
        MuxLookup(scl_state, 1.U, Array(SCL_HIGH -> 1.U, SCL_ACTIVE -> pulse))

    io.sda := 
        MuxLookup(sda_state, 1.U, 
            Array(SDA_HIGH -> 1.U, SDA_LOW -> 0.U, SDA_ACTIVE -> data_out))

    //
    // Defaults
    //

    sda_state := SDA_HIGH
    scl_state := SCL_HIGH

    io.request.ready := false.B
    io.error := false.B
    data_out := data_reg(beat_counter)
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
            io.request.ready := true.B

            when (io.request.fire()) {
                state := s_start

                packet_size := io.request.bits.size

                data_counter := 0.U
                clock_counter := 0.U
                beat_counter := 0.U
                
                packet(0) := io.request.bits.address
                packet(1) := io.request.bits.header

                for (i <- 0 until max_packet_size) {
                    packet(i + 2) := io.request.bits.payload(i)
                }
            }
        }
        is (s_start) {
            // I2C write start condition is signaled by holding SDA low and SCL
            // high. After half a beat, SCL is tied low (by state transition to
            // s_write) and the first beat begins.
            sda_state := SDA_LOW
            scl_state := SCL_HIGH

            // pulse signals when half the beat is over. Transition to s_write
            // here and start outputting data.
            when (pulse) {
                clock_counter := 0.U
                data_reg := Vec(packet(0).toBools)
                state := s_write
            }
        }
        is (s_write) {
            sda_state := SDA_ACTIVE
            scl_state := SCL_ACTIVE

            when (beat_finished) {
                clock_counter := 0.U

                when (beat_counter === 8.U) {
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

            when (rising_edge && io.sda_fb) {
                state := s_error
            }
        }
        is (s_error) {
            io.error := true.B
        }
        is (s_stop) {
            sda_state := SDA_LOW
            scl_state := SCL_HIGH

            when (pulse) {
                state := s_idle
            }
        }
    }
}