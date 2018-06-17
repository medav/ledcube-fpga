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
        val uart_tx = Output(Bool())
        val uart_rx = Input(Bool())
        val bram_read = new BramReadInterface(bram_size)
        val bram_write = new BramWriteInterface(bram_size)
    })

    //
    // Modules
    //

    val csrs = Module(new CsrModule).io
    val comm = Module(new CommController(cube_size)).io

    val rc =
        Module(
            new RefreshController(
                cube_size,
                num_controllers,
                leds_per_controller)).io

    //
    // Connections
    //

    csrs.csr_write <> comm.csr_write
    io.bram_write <> comm.bram_write
    comm.uart_rx := io.uart_rx
    io.uart_tx := comm.uart_tx

    rc.config := csrs.config
    rc.i2c_interfaces <> io.i2c_interfaces
    rc.layer_active <> io.layer_active
    io.bram_read <> rc.bram_read
}