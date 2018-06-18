package ledcube

import chisel3._
import chisel3.util._
import ledcube.interfaces._
import ledcube.constants._
import ledcube.constants.TlcConstants._

class UartTransmitter(
    clock_rate : Int = 50000000,
    baud_rate : Int = 2000000,
    fifo_size : Int = 8)
    extends Module {

    val io = IO(new Bundle {
        val uart_tx = Output(Bool())
        val enqueue = Input(Bool())
        val enqueue_data = Input(UInt(8.W))
        val ready = Output(Bool())
    })

    // N.B. This is integer division on purpose
    val clocks_per_bit = clock_rate / baud_rate

    val s_idle :: s_start :: s_write :: s_stop :: Nil = Enum(4)
    val state = RegInit(s_idle)

    val tx_buf = RegInit(Vec(Seq.fill(8)(false.B)))

    val clock_counter = RegInit(0.U(32.W))
    val bit_counter = RegInit(0.U(4.W))

    //
    // This module contains a fifo for outgoing bytes. This is implemented as a
    // circular buffer. There is logic to prevent overruns and underruns.
    //

    val fifo_ram = Reg(Vec(fifo_size, UInt(8.W)))
    val enq_addr = RegInit(0.asUInt(log2Ceil(fifo_size).W))
    val deq_addr = RegInit(0.asUInt(log2Ceil(fifo_size).W))
    val data_count = RegInit(0.U(log2Ceil(fifo_size + 1).W))
    val full = Wire(Bool())
    val empty = Wire(Bool())
    val enqueue = WireInit(false.B)
    val dequeue = WireInit(false.B)
    val dequeue_data = Wire(UInt(8.W))

    full := data_count >= fifo_size.U
    empty := data_count === 0.U
    dequeue_data := fifo_ram(deq_addr)
    io.ready := !full | dequeue

    switch (Cat(enqueue, dequeue)) {
        is ("b01".U) {

            //
            // Dequeue only.
            //

            when (!empty) {
                data_count := data_count - 1.U
                deq_addr := deq_addr + 1.U
            }
        }

        is ("b10".U) {

            //
            // Enqueue only.
            //

            when (!full) {
                data_count := data_count + 1.U
                enq_addr := enq_addr + 1.U
                fifo_ram(enq_addr) := io.enqueue_data
            }
        }

        is ("b11".U) {

            //
            // Enqueue AND Dequeue.
            //

            deq_addr := deq_addr + 1.U
            enq_addr := enq_addr + 1.U
            fifo_ram(enq_addr) := io.enqueue_data
        }
    }

    when (dequeue) {

        //
        // When data is dequeued off the fifo, it is split into a vec of bools
        // and latched in the tx_buf.
        //

        tx_buf := dequeue_data.toBools
    }

    //
    // Defaults
    //

    io.uart_tx := true.B
    clock_counter := clock_counter + 1.U

    //
    // FSM logic
    //

    switch (state) {
        is (s_idle) {

            //
            // In this state, the transmitter is ready to start sending data.
            // It waits until there is data available in the fifo. Once there
            // is, it immediately dequeues the data and starts transmitting.
            //

            clock_counter := 0.U
            bit_counter := 0.U

            when (!empty) {
                dequeue := true.B
                state := s_start
            }
        }

        is (s_start) {

            //
            // In this state, the start bit is sent over the line. This is a
            // logical '0'. All bits including the start and stop bits must
            // be held for clocks_per_bit number of cycles.
            //

            io.uart_tx := false.B

            when (clock_counter > clocks_per_bit.U) {
                state := s_write
            }
        }

        is (s_write) {

            //
            // In this state, actual data is being written over the line. Each
            // bit is held for clocks_per_bits cycles before moving on to the
            // next one.
            //

            io.uart_tx := tx_buf(bit_counter)

            when (clock_counter > clocks_per_bit.U) {
                when (bit_counter === 7.U) {
                    state := s_stop
                }
                .otherwise {
                    bit_counter := bit_counter + 1.U
                }
            }
        }

        is (s_stop) {

            //
            // In this state, the stop bit (which is a logic '1') is sent over
            // the line. Same deal as pervious states.
            //

            when (clock_counter > clocks_per_bit.U) {
                state := s_idle
            }
        }
    }
}