package ledcube

import chisel3._
import chisel3.util._
import ledcube.interfaces._
import ledcube.constants._
import ledcube.constants.TlcConstants._

class RefreshController(
    cube_size : Int = 8,
    num_controllers : Int = 4,
    leds_per_controller : Int = 16)
    extends Module {

    val bram_size = cube_size * cube_size * cube_size

    val io = IO(new Bundle {
        val config = Input(new RefreshConfig())
        val i2c_interfaces = Vec(num_controllers, new I2c)
        val layer_active = Output(UInt(cube_size.W))
        val bram_read = new BramReadInterface(bram_size)
    })

    //
    // It is assumed that the total number of outputs via the tlc controllers
    // is equal to the number of LEDs in one layer of the cube.
    //

    require(leds_per_controller * num_controllers == cube_size * cube_size)

    val s_init :: s_display :: s_update :: Nil = Enum(3)
    val state = RegInit(s_init)

    val controllers =
        Vec(Seq.fill(num_controllers) { Module(new TlcController()).io })

    val reader = Module(new MemReader(cube_size)).io
    val layer_counter = RegInit((cube_size - 1).U(log2Ceil(cube_size).W))
    val next_layer = Wire(UInt(log2Ceil(cube_size).W))
    val cycle_counter = RegInit(0.U(32.W))
    val controllers_ready = Wire(Bool())
    val controllers_clear = WireInit(false.B)
    val controllers_update = WireInit(false.B)

    next_layer := layer_counter + 1.U

    io.bram_read <> reader.bram_read
    reader.start := false.B
    reader.layer := next_layer

    controllers_ready :=
        Vec.tabulate(num_controllers) {
            i => controllers(i).ready
        } .reduce(_&_)

    io.layer_active := 0.U

    //
    // Defaults and interface connections for each controller
    //

    for (i <- 0 until num_controllers) {
        controllers(i).clear := controllers_clear
        controllers(i).update := controllers_update

        controllers(i).config := io.config.tlc_config
        controllers(i).i2c <> io.i2c_interfaces(i)

        for (j <- 0 until leds_per_controller) {
            controllers(i).led_state_in(j) := reader.led_state_out(i * j)
        }
    }

    //
    // State transition logic (Mainly for control signals)
    //

    switch (state) {
        is (s_init) {

            //
            // In this state, the first read cycle is kicked off and the
            // refresh loop starts by transitioning to s_clear.
            //
            // N.B. This state is effectively the same as s_display except here
            // layer outputs are held low because there is nothing to display,
            // and transition happens immediately.
            //

            reader.start := true.B
            state := s_update
        }

        is (s_display) {

            //
            // In this state, the LEDs are actively displaying. In addition,
            // a new read cycle is started (async) so that the data is ready by
            // the time the next update comes around.
            //

            cycle_counter := cycle_counter + 1.U
            io.layer_active := (1.U << layer_counter)

            when (cycle_counter === 0.U) {
                reader.start := true.B
            }

            when (cycle_counter === io.config.display_cycles) {
                state := s_update
            }
        }

        is (s_update) {

            //
            // In this state, wait for controllers and reader to be ready. Once
            // they are, send the update signal. This will cause the controllers
            // to latch the data from the reader and begin writing it over the
            // i2c port.
            //

            when (controllers_ready & reader.done) {
                controllers_update := true.B
                cycle_counter := 0.U
                state := s_display
                layer_counter := next_layer
            }
        }
    }
}