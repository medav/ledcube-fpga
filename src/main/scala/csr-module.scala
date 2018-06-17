package ledcube

import chisel3._
import chisel3.util._
import ledcube.interfaces._
import ledcube.constants._
import ledcube.constants.TlcConstants._

class CsrModule() extends Module {

    val io = IO(new Bundle {
        val csr_write = Flipped(new CsrWriteInterface)
        val config = Output(new RefreshConfig)
    })

    def BindCsr(address : Int, signal : UInt, num_bytes : Int = 4) = {
        when (io.csr_write.address === address.U) {
            num_bytes match {
                case 1 => signal := io.csr_write.data(7, 0)
                case 2 => signal := io.csr_write.data(15, 0)
                case 4 => signal := io.csr_write.data
                case _ => throw new Exception("Invalid num_bytes")
            }

        }
    }

    def DefineCsr(output_signal : UInt, address : Int, num_bytes : Int, default_value : String) = {
        val csr_reg = RegInit(default_value.U((num_bytes * 8).W))
        output_signal := csr_reg
        BindCsr(address, csr_reg, num_bytes)
    }

    DefineCsr(io.config.display_cycles, 0, 4, "d71667")
    DefineCsr(io.config.tlc_config.mode1, 1, 1, "h11")
    DefineCsr(io.config.tlc_config.mode2, 2, 1, "h00")
    DefineCsr(io.config.tlc_config.iref, 3, 1, "hCF")
    DefineCsr(io.config.tlc_config.i2c_config.clock_threshold, 4, 4, "d50")
    DefineCsr(io.config.tlc_config.i2c_config.clock_period, 4, 4, "d100")
}