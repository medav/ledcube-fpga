package ledcube

import chisel3._
import chisel3.util._
import ledcube.interfaces._
import ledcube.constants._
import ledcube.constants.TlcConstants._

class MemReader(cube_size : Int = 8) extends Module {
    val bram_size = cube_size * cube_size * cube_size
    val layer_size = cube_size * cube_size

    val io = IO(new Bundle {
        val bram_read = new BramReadInterface(bram_size)
        val start = Input(Bool())
        val layer = Input(UInt(log2Ceil(cube_size).W))
        val led_state_out = Output(Vec(layer_size, UInt(8.W)))
        val done = Output(Bool())
    })

    val led_state = Reg(Vec(layer_size, UInt(8.W)))
    val read_counter = RegInit(0.U)
    val base_address = RegInit(0.U)
    val done = RegInit(false.B)
    val capture_read = RegInit(false.B)
    val capture_counter = RegNext(read_counter)

    //
    // Start overrides the current read cycle. It is assumed that whatever
    // module manages this knows what it's doing.
    //

    when (io.start) {
        read_counter := 0.U
        done := false.B

        //
        // The following code will effectively instantiate a lookup table of
        // constant offsets to use for the start address of the read. This
        // should work fine since cube_size is assumed to be relatively small.
        //

        for (layer_index <- 0 until cube_size) {
            when (io.layer === layer_index.U) {
                base_address := (layer_index * layer_size).U
            }
        }
    }

    //
    // This module requires exclusive access to a read port since there is not
    // "ready" signal on the read interface. While there is still more data to
    // read, this module will always be actively reading an address.
    //

    capture_read := false.B
    io.bram_read.address := base_address + read_counter

    when (!done) {
        capture_read := read_counter < layer_size.U
        read_counter := read_counter + 1.U

        when (read_counter === layer_size.U) {
            done := true.B
        }
    }

    when (capture_read) {
        led_state(capture_counter) := io.bram_read.data
    }

    io.done := done
    io.led_state_out := led_state
}