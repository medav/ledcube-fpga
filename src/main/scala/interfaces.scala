package ledcube.interfaces

import chisel3._

class I2c extends Bundle {
    val scl = Bool()
    val scl_fb = Flipped(Bool())
    val sda = Bool()
    val resetn = Bool()
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
    val clock_threshold = UInt(64.W)
    val clock_period = UInt(64.W)
}

class TlcConfig extends Bundle {
    val i2c_config = new I2cConfig()
    val mode1 = UInt(8.W)
    val mode2 = UInt(8.W)
    val iref = UInt(8.W)
}