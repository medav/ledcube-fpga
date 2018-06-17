package ledcube.interfaces

import chisel3._
import chisel3.util._

class PullDownPin extends Bundle {
    val out = Output(Bool())
    val in = Input(Bool())
}

class I2c extends Bundle {
    val sda = new PullDownPin()
    val scl = new PullDownPin()
    val resetn = Output(Bool())
}

class BramReadInterface(bram_size : Int = 512) extends Bundle {
    val address = Output(UInt(log2Ceil(bram_size).W))
    val data = Input(UInt(8.W))

    override def cloneType(): this.type =
        (new BramReadInterface(bram_size)).asInstanceOf[this.type]
}

class BramWriteInterface(bram_size : Int = 512) extends Bundle {
    val address = Output(UInt(log2Ceil(bram_size).W))
    val write = Output(Bool())
    val data = Output(UInt(8.W))

    override def cloneType(): this.type =
        (new BramWriteInterface(bram_size)).asInstanceOf[this.type]
}

class CsrWriteInterface() extends Bundle {
    val address = Output(UInt(8.W))
    val data = Output(UInt(32.W))
    val write = Output(Bool())
}

class I2cPacket(max_packet_size : Int = 16) extends Bundle {
    val size = UInt(8.W)
    val address = UInt(8.W)
    val header = UInt(8.W)
    val payload = Vec(max_packet_size, UInt(8.W))

    override def cloneType(): this.type =
        (new I2cPacket(max_packet_size)).asInstanceOf[this.type]
}

class I2cConfig extends Bundle {
    val clock_threshold = UInt(32.W)
    val clock_period = UInt(32.W)
}

class TlcConfig extends Bundle {
    val i2c_config = new I2cConfig()
    val mode1 = UInt(8.W)
    val mode2 = UInt(8.W)
    val iref = UInt(8.W)
}

class RefreshConfig extends Bundle {
    val tlc_config = new TlcConfig()
    val display_cycles = UInt(32.W)
}