package ledcube

import chisel3._
import chisel3.util._
import ledcube.interfaces._
import ledcube.constants._
import ledcube.constants.TlcConstants._

class UartReceiver(
    clock_rate : Int = 50000000,
    baud_rate : Int = 2000000,
    fifo_size : Int = 4)
    extends Module {

    val io = IO(new Bundle {
        val uart_rx = Input(Bool())
        val dequeue = Input(Bool())
        val dequeue_data = Output(UInt(8.W))
        val data_available = Output(Bool())
    })

    //
    // This UART is a tad non-standard. According to "the internet," best
    // practice is to have the receiving module generate a clock that is 8x the
    // desired baudrate. Since this module is operating on a 50 MHz reference
    // clock, generating a baud clock that is 8x {1, 2, 4} MHz is going to
    // produce a lot of error. Thus, this module forgoes the baud clock and
    // just maintains some counters for various threshold values to trigger
    // things to happen.
    //

    // N.B. This is integer division on purpose
    val clocks_per_bit = clock_rate / baud_rate
    val clocks_per_half_bit = clocks_per_bit / 2

    val s_idle :: s_start :: s_read :: s_stop :: Nil = Enum(4)
    val state = RegInit(s_idle)

    //
    // Register where the current byte to be received is placed.
    //

    val data_reg = RegInit(Vec(Seq.fill(8)(false.B)))

    //
    // This module contains a fifo for incoming bytes. This is implemented as a
    // circular buffer.
    //
    // N.B. Currently there is no logic to prevent buffer overruns since it is
    // assumed the controlling logic can handle bytes faster than this module
    // can receive them.
    //

    val fifo_ram = Reg(Vec(fifo_size, UInt(8.W)))
    val enq_addr = RegInit(0.asUInt(log2Ceil(fifo_size).W))
    val deq_addr = RegInit(0.asUInt(log2Ceil(fifo_size).W))
    val enqueue = WireInit(false.B)

    //
    // Counters to keep track of which bit and which cycle this module is on.
    //

    val clock_counter = RegInit(0.U(32.W))
    val bit_counter = RegInit(0.U(4.W))

    //
    // Defaults
    //

    clock_counter := clock_counter + 1.U
    io.data_available := (enq_addr =/= deq_addr)
    io.dequeue_data := fifo_ram(deq_addr)

    //
    // Enqueue / Dequeue logic
    //

    when (enqueue) {
        fifo_ram(enq_addr) := data_reg.asUInt
        enq_addr := enq_addr + 1.U
    }

    when (io.dequeue & (enq_addr =/= deq_addr)) {
        deq_addr := deq_addr + 1.U
    }

    //
    // FSM logic
    //

    switch (state) {
        is (s_idle) {

            //
            // In this state, the receiver is idle and waits for the first
            // falling edge of the rx line. The clock_counter is tied to 0
            // since there is no need to count up in this state.
            //

            clock_counter := 0.U

            when (io.uart_rx === false.B) {
                state := s_start
            }
        }

        is (s_start) {

            //
            // In this state, the start bit is captured. If the line goes high
            // before half a bit's worth of clocks, it is assumed to be an
            // invalid start condition and this module gives up and goes to
            // idle. When the full start bit is received, this module begins
            // to read the rest of the bits.
            //

            when ((io.uart_rx === true.B) &
                (clock_counter < clocks_per_half_bit.U)) {

                state := s_idle
            }

            when (clock_counter >= clocks_per_bit.U) {
                state := s_read
                clock_counter := 0.U
                bit_counter := 0.U

                for (i <- 0 until 8) {
                    data_reg(i) := false.B
                }
            }
        }

        is (s_read) {

            //
            // In this state, this module is actively receiving bits. This is
            // mainly tracked through the bit and clock counters. Once all 8
            // bits are recorded, this module transitions to the stop state.
            //

            when (clock_counter === clocks_per_half_bit.U) {
                data_reg(bit_counter) := io.uart_rx
            }

            when (clock_counter === clocks_per_bit.U) {
                when (bit_counter === 7.U) {
                    state := s_stop
                    clock_counter := 0.U
                }
                .otherwise {
                    bit_counter := bit_counter + 1.U
                    clock_counter := 0.U
                }
            }
        }

        is (s_stop) {

            //
            // In this state, the receiver is finishing up the read. Since this
            // module implements typical 8N1 UART protocol, it just needs to
            // wait for one whole stop bit before transitioning to idle.
            //

            when (clock_counter === clocks_per_bit.U) {
                state := s_idle
                enqueue := true.B
            }
        }
    }
}