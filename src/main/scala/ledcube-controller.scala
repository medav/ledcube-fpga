package ledcube

import chisel3._
import chisel3.util._
import ledcube.interfaces._
import ledcube.constants._
import ledcube.constants.TlcConstants._

class LedCubeController(
    cube_size : Int = 8,
    num_controllers : Int = 4,
    leds_per_controller : Int = 16)
    extends Module {

    val bram_size = cube_size * cube_size * cube_size

    val io = IO(new Bundle {
        val i2c_interfaces = Vec(num_controllers, new I2c)
        val layer_active = Output(UInt(cube_size.W))
        val bram_read = new BramReadInterface(bram_size)
    })

    //
    // Control / Status Registers (CSRs) and associated defaults.
    //

    val i2c_clock_threshold = RegInit(50.U(32.W))
    val i2c_clock_period = RegInit(100.U(32.W))
    val tlc_mode1 = RegInit("h11".U(8.W))
    val tlc_mode2 = RegInit("h00".U(8.W))
    val tlc_iref = RegInit("hCF".U(8.W))
    val rc_display_cycles = RegInit(71667.U(32.W))

    val rc = 
        Module(
            new RefreshController(
                cube_size, 
                num_controllers, 
                leds_per_controller)).io

    rc.config.display_cycles := rc_display_cycles
    rc.config.tlc_config.mode1 := tlc_mode1
    rc.config.tlc_config.mode2 := tlc_mode2
    rc.config.tlc_config.iref := tlc_iref
    rc.config.tlc_config.i2c_config.clock_threshold := i2c_clock_threshold
    rc.config.tlc_config.i2c_config.clock_period := i2c_clock_period

    rc.i2c_interfaces <> io.i2c_interfaces
    rc.layer_active <> io.layer_active
    io.bram_read <> rc.bram
}