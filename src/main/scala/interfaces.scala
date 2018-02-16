package ledcube.interfaces

import chisel3._

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
