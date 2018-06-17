package ledcube

import chisel3._
import chisel3.util._
import ledcube.interfaces._
import ledcube.constants._
import ledcube.constants.CommConstants._

class CommController(cube_size : Int = 8) extends Module {
    val bram_size = cube_size * cube_size * cube_size

    val io = IO(new Bundle {
        val uart_tx = Output(Bool())
        val uart_rx = Input(Bool())
        val csr_write = new CsrWriteInterface
        val bram_write = new BramWriteInterface
    })

    val s_idle :: s_read_cmd :: s_csrw :: s_readframe :: Nil = Enum(4)
    val state = RegInit(s_idle)
    val uart_receiver = Module(new UartReceiver).io
    val data_in = WireInit(0.U(8.W))
    val read_counter = RegInit(0.U(32.W))
    val csr_addr = RegInit(0.U(8.W))
    val csr_value = RegInit(Vec(Seq.fill(3){ 0.U(8) }))

    //
    // Interface defaults
    //

    io.csr_write.write := false.B
    io.csr_write.address := csr_addr
    io.csr_write.data := Cat(data_in, csr_value.asUInt)

    // N.B. This will truncate read_counter down to however many address bits
    // are available. That's fine (and expected).
    io.bram_write.address := read_counter
    io.bram_write.data := data_in
    io.bram_write.write := false.B

    io.uart_tx := true.B

    //
    // Internal defaults
    //

    uart_receiver.uart_rx := io.uart_rx
    uart_receiver.dequeue := false.B
    data_in := uart_receiver.dequeue_data

    //
    // Unless overridden in the FSM logic, it is assumed that every byte will
    // be dequeued when available so read_counter defaults to increment on
    // data_available.
    //

    when (uart_receiver.data_available) {
        read_counter := read_counter + 1.U
    }

    //
    // FSM logic
    //

    switch (state) {
        is (s_idle) {

            //
            // In this state, the module is waiting for a command. Command
            // opcodes are exactly 1 byte and will cause this module to
            // transition to the corresponding state to handle that command.
            //

            uart_receiver.dequeue := true.B

            when (uart_receiver.data_available) {
                read_counter := 0.U

                when (data_in === OP_CSRW) {
                    state := s_csrw
                }
                .elsewhen (data_in === OP_READFRAME) {
                    state := s_readframe
                }
            }
        }

        is (s_csrw) {

            //
            // In this state, the module has been directed to perform a CSR
            // write. This command is 5 bytes:
            //
            //     0: address
            //     1: value[0]
            //     2: value[1]
            //     3: value[2]
            //     4: value[3]
            //
            // N.B. Some of the CSRs are 8-bit registers but from an interface
            // perspective, all CSRW commands take 5 bytes for consistency. It
            // is the job of the controller on the other side of the UART to
            // pad the data with 0's for 8- and 16-bit CSRs. It is assumed that
            // CSRW's don't happen often enough for the extra padding to hurt
            // performance significantly.
            //

            uart_receiver.dequeue := true.B

            when (uart_receiver.data_available) {
                when (read_counter === 0.U) {
                    csr_addr := data_in
                }
                .elsewhen (read_counter < 4.U) {
                    csr_value(read_counter - 1.U) := data_in
                }
                .elsewhen (read_counter === 4.U) {

                    //
                    // N.B. The last byte bypasses the value register so the
                    // write can be fired when the receiver says the last byte
                    // is available.
                    //

                    state := s_idle
                    io.csr_write.write := true.B
                }
            }
        }

        is (s_readframe) {

            //
            // In this state, this module reads a whole frame from the UART and
            // writes it to the bram. This will read bram_size # of bytes. The
            // bram is dual ported so reads and writes can happen without the
            // need for arbitration. Also, read/write races aren't a problem
            // since at worst it will cause frame tearing for a fraction of a
            // second.
            //

            uart_receiver.dequeue := true.B
            io.bram_write.write := uart_receiver.data_available

            when ((read_counter === (bram_size - 1).U) &
                uart_receiver.data_available) {

                state := s_idle
            }
        }
    }
}